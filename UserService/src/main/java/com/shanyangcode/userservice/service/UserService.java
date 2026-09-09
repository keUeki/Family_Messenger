package com.shanyangcode.userservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shanyangcode.userservice.model.dto.request.UpdateAvatarRequest;
import com.shanyangcode.userservice.model.dto.request.UpdatePasswordRequest;
import com.shanyangcode.userservice.model.dto.request.UserLoginCodeRequest;
import com.shanyangcode.userservice.model.dto.request.UserLoginPasswordRequest;
import com.shanyangcode.userservice.model.dto.request.UserRegisterRequest;
import com.shanyangcode.userservice.model.entity.User;
import com.shanyangcode.userservice.model.vo.LoginAndRegisterResponse;
import com.shanyangcode.userservice.model.vo.TokenResponse;
import com.shanyangcode.userservice.model.vo.UploadUrlResponse;
import com.shanyangcode.userservice.model.vo.UserInfoResponse;

import java.util.Map;


public interface UserService extends IService<User> {

    void sendCaptcha(String targetEmail);

    LoginAndRegisterResponse register(UserRegisterRequest userRegisterRequest);

    LoginAndRegisterResponse loginPassword(UserLoginPasswordRequest userLoginPasswordRequest);

    LoginAndRegisterResponse loginCode(UserLoginCodeRequest userLoginCodeRequest);

    boolean logout(String userId);

    TokenResponse refreshToken(String refreshToken);

    String refreshUri(Long userId);

    UploadUrlResponse uploadUrl(String fileName) ;

    Boolean updateAvatar(UpdateAvatarRequest updateAvatarRequest);

    Map<Long, String> getUserNickName(Long sessionId);

    /**
     * 查询用户资料
     *
     * @param userId 用户 ID
     * @return 用户资料
     */
    UserInfoResponse getUserInfo(Long userId);

    /**
     * 通过邮箱验证码修改密码
     *
     * @param updatePasswordRequest 修改密码请求
     * @return 是否修改成功
     */
    Boolean updatePassword(UpdatePasswordRequest updatePasswordRequest);
}
