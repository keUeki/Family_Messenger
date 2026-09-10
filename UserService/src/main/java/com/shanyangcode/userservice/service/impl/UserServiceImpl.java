package com.shanyangcode.userservice.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.CommonConstant;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.userservice.constants.UserConstant;
import com.shanyangcode.userservice.loadbalancer.NettyServiceLocator;
import com.shanyangcode.userservice.mapper.UserMapper;
import com.shanyangcode.userservice.model.dto.request.UpdateAvatarRequest;
import com.shanyangcode.userservice.model.dto.request.UpdatePasswordRequest;
import com.shanyangcode.userservice.model.dto.request.UserLoginCodeRequest;
import com.shanyangcode.userservice.model.dto.request.UserLoginPasswordRequest;
import com.shanyangcode.userservice.model.dto.request.UserRegisterRequest;
import com.shanyangcode.userservice.model.entity.User;
import com.shanyangcode.userservice.model.entity.UserSession;
import com.shanyangcode.userservice.model.entity.Session;
import com.shanyangcode.userservice.model.vo.LoginAndRegisterResponse;
import com.shanyangcode.userservice.model.vo.TokenResponse;
import com.shanyangcode.userservice.model.vo.UploadUrlResponse;
import com.shanyangcode.userservice.model.vo.UserInfoResponse;
import com.shanyangcode.userservice.service.SessionService;
import com.shanyangcode.userservice.service.UserService;

import com.shanyangcode.userservice.service.UserSessionService;
import com.shanyangcode.userservice.utils.EmailUtil;
import com.shanyangcode.common.utils.JwtUtil;
import com.shanyangcode.userservice.utils.OssUtils;
import com.shanyangcode.userservice.utils.RandomCodeUtil;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService {

    @Resource
    private EmailUtil emailUtil;

    @Resource
    private NettyServiceLocator serviceInstanceUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserSessionService userSessionService;

    @Lazy
    @Resource
    private SessionService sessionService;

    @Override
    public void sendCaptcha(String targetEmail) {
        String existingCode = stringRedisTemplate.opsForValue().get(targetEmail);
        ThrowUtils.throwIf(StringUtils.isNotBlank(existingCode), ErrorCode.LOGIN_ERROR_CODE);

        String randomCode = RandomCodeUtil.getRandomCode();
        emailUtil.sendEmail(targetEmail, randomCode);
        stringRedisTemplate.opsForValue().set(targetEmail, randomCode, UserConstant.CAPTCHA_EXPIRE_TIME, TimeUnit.MINUTES);
    }



    @Override
    public LoginAndRegisterResponse register(UserRegisterRequest userRegisterRequest) {

        String email = userRegisterRequest.getEmail();
        String code = userRegisterRequest.getCode();
        // Check that the verification code is correct
        String redisCode = stringRedisTemplate.opsForValue().get(email);
        ThrowUtils.throwIf(StringUtils.isBlank(redisCode) || !code.equals(redisCode), ErrorCode.LOGIN_ERROR_CODE);

        // Check whether the account already exists
        ThrowUtils.throwIf(getUser(email) != null, ErrorCode.USER_ALREADY_EXISTS);


        // Check that the two passwords match
        ThrowUtils.throwIf(!userRegisterRequest.getPassword().equals(userRegisterRequest.getConfirmPassword()), ErrorCode.LOGIN_ERROR);


        String password = userRegisterRequest.getPassword();
        String encryptedPassword = DigestUtils.md5DigestAsHex((UserConstant.PASSWORD_SALT + password).getBytes());


        LoginAndRegisterResponse loginAndRegisterResponse = new LoginAndRegisterResponse();

        Snowflake snowflake = IdUtil.getSnowflake(UserConstant.WORKER_ID, UserConstant.DATA_CENTER_ID);
        Long userId = snowflake.nextId();
        synchronized (email.intern()) {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setUserId(userId);
            newUser.setNickname(userRegisterRequest.getNickname());
            newUser.setPassword(encryptedPassword);
            boolean saveUser = this.save(newUser);
            ThrowUtils.throwIf(!saveUser, ErrorCode.SYSTEM_ERROR);
            BeanUtil.copyProperties(getUser(email), loginAndRegisterResponse);
        }



        Long sessionId = snowflake.nextId();
        Session session = new Session();
        session.setSessionId(sessionId);
        session.setStatus(CommonConstant.SESSION_STATUS);
        session.setType(SessionTypeConstant.ROBOT_TYPE);
        ThrowUtils.throwIf(!sessionService.save(session), ErrorCode.SYSTEM_ERROR);


        // Create the user session (a regular user)
        UserSession userSessionUser = createUserSession(userId, sessionId, CommonConstant.USER_ROLE_NORMAL, CommonConstant.SESSION_STATUS);

        // Create the AI session
        UserSession userSessionAI = createUserSession(CommonConstant.AI_ID, sessionId, CommonConstant.USER_ROLE_NORMAL, CommonConstant.SESSION_STATUS);

        // Add it to the list
        List<UserSession> sessionList = Arrays.asList(userSessionUser, userSessionAI);

        // Save them in one batch
        ThrowUtils.throwIf(!userSessionService.saveBatch(sessionList), ErrorCode.SYSTEM_ERROR);

        stringRedisTemplate.delete(email);
        return createJwt(loginAndRegisterResponse);
    }

    public UserSession createUserSession(Long userId, Long sessionId, Integer role, Integer stats) {
        UserSession userSession = new UserSession();
        userSession.setUserId(userId);
        userSession.setSessionId(sessionId);
        userSession.setRole(role);
        userSession.setStatus(stats);
        return userSession;
    }

    @Override
    public LoginAndRegisterResponse loginPassword(UserLoginPasswordRequest userLoginPasswordRequest) {
        String email = userLoginPasswordRequest.getEmail();
        String password = userLoginPasswordRequest.getPassword();

        User user = getUser(email);
        ThrowUtils.throwIf(user == null, ErrorCode.USER_NOT_EXISTS);

        String encryptedPassword = DigestUtils.md5DigestAsHex((UserConstant.PASSWORD_SALT + password).getBytes());
        ThrowUtils.throwIf(!encryptedPassword.equals(user.getPassword()), ErrorCode.LOGIN_ERROR);

        LoginAndRegisterResponse loginAndRegisterResponse = new LoginAndRegisterResponse();
        BeanUtil.copyProperties(user, loginAndRegisterResponse);
        return createJwt(loginAndRegisterResponse);

    }

    @Override
    public LoginAndRegisterResponse loginCode(UserLoginCodeRequest userLoginCodeRequest) {
        String email = userLoginCodeRequest.getEmail();
        String code = userLoginCodeRequest.getCode();

        String redisCode = stringRedisTemplate.opsForValue().get(email);
        ThrowUtils.throwIf(StringUtils.isBlank(redisCode) || !code.equals(redisCode),ErrorCode.LOGIN_ERROR_CODE);

        // Delete the verification code held in Redis
        stringRedisTemplate.delete(email);

        User user = getUser(email);
        ThrowUtils.throwIf(user == null, ErrorCode.USER_NOT_EXISTS);

        LoginAndRegisterResponse loginAndRegisterResponse = new LoginAndRegisterResponse();
        BeanUtil.copyProperties(user, loginAndRegisterResponse);

        return createJwt(loginAndRegisterResponse);
    }


    public User getUser(String email) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("email", email);
        return this.getOne(queryWrapper);
    }



    public LoginAndRegisterResponse createJwt(LoginAndRegisterResponse loginAndRegisterResponse) {
        String userId = loginAndRegisterResponse.getUserId().toString();
        String accessToken = JwtUtil.generate(userId, CommonConstant.ACCESS_TOKEN_EXPIRE_TIME, CommonConstant.ACCESS_TOKEN_UNIT);
        String refreshToken = JwtUtil.generate(userId, CommonConstant.REFRESH_TOKEN_EXPIRE_TIME, CommonConstant.REFRESH_TOKEN_UNIT);
        loginAndRegisterResponse.setAccessToken(accessToken);
        loginAndRegisterResponse.setRefreshToken(refreshToken);
        stringRedisTemplate.opsForValue().set(CommonConstant.ACCESS_TOKEN_PREFIX + userId, accessToken, CommonConstant.ACCESS_TOKEN_EXPIRE_TIME, CommonConstant.ACCESS_TOKEN_UNIT);
        stringRedisTemplate.opsForValue().set(CommonConstant.REFRESH_TOKEN_PREFIX + userId, refreshToken, CommonConstant.REFRESH_TOKEN_EXPIRE_TIME, CommonConstant.REFRESH_TOKEN_UNIT);
        String nettyUri = serviceInstanceUtil.getServiceInstance(loginAndRegisterResponse.getUserId().toString());
        loginAndRegisterResponse.setNettyUri(nettyUri);

        Long offlineTime = getAndClearOfflineTime(userId);
        loginAndRegisterResponse.setOfflineTime(offlineTime);
        return loginAndRegisterResponse;
    }

    private Long getAndClearOfflineTime(String userId) {
        String key = CommonConstant.OFFLINE_KEY_REDIS + userId;
        String value = stringRedisTemplate.opsForValue().getAndDelete(key);
        if (StringUtils.isNotBlank(value)) {
            log.info("User {} came online, offline since: {}", userId, value);
            return Long.parseLong(value);
        }
        // A new user, or a first login, so there is no offline record
        log.debug("User {} has no recorded offline time", userId);
        return null;
    }

    @Override
    public boolean logout(String userId) {
        stringRedisTemplate.delete(CommonConstant.ACCESS_TOKEN_PREFIX + userId);
        stringRedisTemplate.delete(CommonConstant.REFRESH_TOKEN_PREFIX + userId);
        return true;
    }


    @Override
    public TokenResponse refreshToken(String refreshToken) {
        // 1. Parse the supplied refresh token
        Claims claims = JwtUtil.parse(refreshToken);
        ThrowUtils.throwIf(claims == null, ErrorCode.TOKEN_INVALID, "Your credentials are no longer valid, please log in again");


        // 2. Safely read the userId out of the payload
        String userId = claims.getSubject();

        // 3. Check Redis to defeat token-revocation attacks; this is what makes single-device login work
        String redisRefreshToken = stringRedisTemplate.opsForValue().get(CommonConstant.REFRESH_TOKEN_PREFIX + userId);
        ThrowUtils.throwIf(!refreshToken.equals(redisRefreshToken), ErrorCode.TOKEN_INVALID, "Your credentials have expired, or you logged in elsewhere");


        // 4. Issue a fresh token pair
        String newAccessToken = JwtUtil.generate(userId, CommonConstant.ACCESS_TOKEN_EXPIRE_TIME, CommonConstant.ACCESS_TOKEN_UNIT);
        String newRefreshToken = JwtUtil.generate(userId, CommonConstant.REFRESH_TOKEN_EXPIRE_TIME, CommonConstant.REFRESH_TOKEN_UNIT);

        // 5. Update Redis
        stringRedisTemplate.opsForValue().set(CommonConstant.ACCESS_TOKEN_PREFIX + userId, newAccessToken, CommonConstant.ACCESS_TOKEN_EXPIRE_TIME, CommonConstant.ACCESS_TOKEN_UNIT);
        stringRedisTemplate.opsForValue().set(CommonConstant.REFRESH_TOKEN_PREFIX + userId, newRefreshToken, CommonConstant.REFRESH_TOKEN_EXPIRE_TIME, CommonConstant.REFRESH_TOKEN_UNIT);
        return TokenResponse.builder().accessToken(newAccessToken).refreshToken(newRefreshToken).build();
    }

    @Override
    public String refreshUri(Long userId) {
        return serviceInstanceUtil.getServiceInstance(String.valueOf(userId));
    }

    @Resource
    private OssUtils ossUtils;

    @Override
    public UploadUrlResponse uploadUrl(String fileName) {
        UploadUrlResponse uploadUrlResponse = new UploadUrlResponse();
        uploadUrlResponse.setUploadUrl(ossUtils.uploadUrl(CommonConstant.BUCKET_NAME, fileName, CommonConstant.PICTURE_EXPIRE_TIME));
        uploadUrlResponse.setDownloadUrl(ossUtils.downUrl(CommonConstant.BUCKET_NAME, fileName));
        return uploadUrlResponse;
    }

    @Override
    public Boolean updateAvatar(UpdateAvatarRequest updateAvatarRequest) {
        User user = this.getById(updateAvatarRequest.getUserId());
        if (user == null) {
            return false;
        }
        user.setAvatar(updateAvatarRequest.getUri());
        return this.updateById(user);
    }

    @Override
    public Map<Long, String> getUserNickName(Long sessionId) {
        List<Long> userIds = userSessionService.getUserIdBySessionId(sessionId);
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("user_id", userIds);
        List<User> users = this.list(queryWrapper);
        return users.stream().collect(Collectors.toMap(User::getUserId, User::getNickname));
    }

    @Override
    public UserInfoResponse getUserInfo(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "User id must not be empty");

        User user = this.getById(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.USER_NOT_EXISTS);

        UserInfoResponse userInfoResponse = new UserInfoResponse();
        userInfoResponse.setUserId(String.valueOf(user.getUserId()));
        userInfoResponse.setAccount(user.getEmail());
        userInfoResponse.setNickname(user.getNickname());
        userInfoResponse.setAvatar(user.getAvatar());
        userInfoResponse.setGender(user.getGender());
        userInfoResponse.setDescription(user.getDescription());
        return userInfoResponse;
    }

    @Override
    public Boolean updatePassword(UpdatePasswordRequest updatePasswordRequest) {
        String email = updatePasswordRequest.getEmail();
        String code = updatePasswordRequest.getCode();

        // 1. Check the verification code
        String redisCode = stringRedisTemplate.opsForValue().get(email);
        ThrowUtils.throwIf(StringUtils.isBlank(redisCode) || !code.equals(redisCode), ErrorCode.LOGIN_ERROR_CODE);

        // 2. Check that the two passwords match
        ThrowUtils.throwIf(!updatePasswordRequest.getPassword().equals(updatePasswordRequest.getConfirmPassword()),
                ErrorCode.LoginPasswordError);

        // 3. Check that the user exists
        User user = getUser(email);
        ThrowUtils.throwIf(user == null, ErrorCode.USER_NOT_EXISTS);

        // 4. Update the password
        String encryptedPassword = DigestUtils.md5DigestAsHex(
                (UserConstant.PASSWORD_SALT + updatePasswordRequest.getPassword()).getBytes());
        user.setPassword(encryptedPassword);
        boolean updated = this.updateById(user);
        ThrowUtils.throwIf(!updated, ErrorCode.SYSTEM_ERROR);

        // 5. The code is single-use, and changing the password forces a fresh login
        stringRedisTemplate.delete(email);
        logout(String.valueOf(user.getUserId()));
        log.info("User {} changed their password; the login state has been cleared", user.getUserId());
        return true;
    }
}




