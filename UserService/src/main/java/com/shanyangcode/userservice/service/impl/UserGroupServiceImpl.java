package com.shanyangcode.userservice.service.impl;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.common.model.dto.PageResponse;
import com.shanyangcode.userservice.mapper.SessionMapper;
import com.shanyangcode.userservice.mapper.UserSessionMapper;
import com.shanyangcode.userservice.model.dto.response.UserGroupDTO;
import com.shanyangcode.userservice.model.entity.Session;
import com.shanyangcode.userservice.model.entity.UserSession;
import com.shanyangcode.userservice.service.UserGroupService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * User group list service implementation
 */
@Slf4j
@Service
public class UserGroupServiceImpl implements UserGroupService {

    private final SessionMapper sessionMapper;
    private final UserSessionMapper userSessionMapper;

    /**
     * User role constants
     */
    private static final int ROLE_OWNER = 0;

    /**
     * Status constants
     */
    private static final int STATUS_NORMAL = 0;

    /**
     * Time format
     */
    private static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public UserGroupServiceImpl(SessionMapper sessionMapper,
                                UserSessionMapper userSessionMapper) {
        this.sessionMapper = sessionMapper;
        this.userSessionMapper = userSessionMapper;
    }

    /**
     * Returns a page of the groups the user belongs to
     *
     * @param userId      the user id
     * @param pageRequest the pagination parameters
     * @return a page of the user's groups
     */
    @Override
    public PageResponse<UserGroupDTO> getUserGroups(Long userId, PageRequest pageRequest) {
        log.info("Loading the user's group list, userId: {}, pageNum: {}, pageSize: {}",
                userId, pageRequest.getPageNum(), pageRequest.getPageSize());

        // 1. Validate the parameters
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "User id must not be empty");

        // 2. Load every sessionId linked to the user that is still active
        LambdaQueryWrapper<UserSession> userSessionWrapper = new LambdaQueryWrapper<>();
        userSessionWrapper.eq(UserSession::getUserId, userId)
                .eq(UserSession::getStatus, STATUS_NORMAL)
                .select(UserSession::getSessionId);
        List<UserSession> userSessions = userSessionMapper.selectList(userSessionWrapper);

        if (userSessions.isEmpty()) {
            log.info("The user has not joined any session, userId: {}", userId);
            return buildEmptyResponse(pageRequest);
        }

        List<Long> sessionIds = userSessions.stream()
                .map(UserSession::getSessionId)
                .toList();

        // 3. Load the group sessions (type=1, status=0)
        LambdaQueryWrapper<Session> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.in(Session::getSessionId, sessionIds)
                .eq(Session::getType, SessionTypeConstant.GROUP_TYPE)
                .eq(Session::getStatus, STATUS_NORMAL);
        List<Session> groupSessions = sessionMapper.selectList(sessionWrapper);

        if (groupSessions.isEmpty()) {
            log.info("The user has not joined any group, userId: {}", userId);
            return buildEmptyResponse(pageRequest);
        }

        // 4. Collect the group session ids
        Set<Long> groupSessionIds = groupSessions.stream()
                .map(Session::getSessionId)
                .collect(Collectors.toSet());

        // 5. Load the user's membership rows for those groups, paginated, most recently joined first
        Page<UserSession> page = pageRequest.toPage();
        LambdaQueryWrapper<UserSession> pagedWrapper = new LambdaQueryWrapper<>();
        pagedWrapper.eq(UserSession::getUserId, userId)
                .in(UserSession::getSessionId, groupSessionIds)
                .eq(UserSession::getStatus, STATUS_NORMAL)
                .orderByDesc(UserSession::getCreatedTime);
        Page<UserSession> resultPage = userSessionMapper.selectPage(page, pagedWrapper);

        if (resultPage.getRecords().isEmpty()) {
            return buildEmptyResponse(pageRequest);
        }

        // 6. Collect the sessionIds on the current page
        List<Long> pagedSessionIds = resultPage.getRecords().stream()
                .map(UserSession::getSessionId)
                .toList();

        // 7. Build the sessionId -> Session map
        Map<Long, Session> sessionMap = groupSessions.stream()
                .filter(s -> pagedSessionIds.contains(s.getSessionId()))
                .collect(Collectors.toMap(Session::getSessionId, s -> s));

        // 8. Look up the owners (role=0) to fill in creatorId
        Map<Long, Long> creatorMap = getCreatorMap(pagedSessionIds);

        // 9. Count the members of each group
        Map<Long, Integer> memberCountMap = getMemberCountMap(pagedSessionIds);

        // 10. Map the rows onto DTOs
        List<UserGroupDTO> dtoList = resultPage.getRecords().stream()
                .map(us -> convertToDTO(us, sessionMap.get(us.getSessionId()), creatorMap, memberCountMap))
                .toList();

        log.info("User group list loaded, userId: {}, total: {}, rows returned: {}",
                userId, resultPage.getTotal(), dtoList.size());

        // 11. Build the paginated response
        return PageResponse.<UserGroupDTO>builder()
                .list(dtoList)
                .total(resultPage.getTotal())
                .pageSize(resultPage.getSize())
                .pageNum(resultPage.getCurrent())
                .pages(resultPage.getPages())
                .hasNext(resultPage.getCurrent() < resultPage.getPages())
                .hasPrevious(resultPage.getCurrent() > 1)
                .build();
    }

    /* ===================== Private helpers ===================== */

    /**
     * Builds an empty paginated response
     */
    private PageResponse<UserGroupDTO> buildEmptyResponse(PageRequest pageRequest) {
        return PageResponse.empty(pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    /**
     * Looks up the owner's user id for each group
     */
    private Map<Long, Long> getCreatorMap(List<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        LambdaQueryWrapper<UserSession> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.in(UserSession::getSessionId, sessionIds)
                .eq(UserSession::getRole, ROLE_OWNER)
                .eq(UserSession::getStatus, STATUS_NORMAL)
                .select(UserSession::getSessionId, UserSession::getUserId);
        List<UserSession> owners = userSessionMapper.selectList(ownerWrapper);

        return owners.stream()
                .collect(Collectors.toMap(
                        UserSession::getSessionId,
                        UserSession::getUserId,
                        (existing, replacement) -> existing // if there is more than one owner, keep the first
                ));
    }

    /**
     * Counts the members of each group
     */
    private Map<Long, Integer> getMemberCountMap(List<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Count the members of each group in turn
        return sessionIds.stream()
                .collect(Collectors.toMap(
                        sessionId -> sessionId,
                        this::countGroupMembers
                ));
    }

    /**
     * Counts the members of a single group
     */
    private Integer countGroupMembers(Long sessionId) {
        LambdaQueryWrapper<UserSession> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(UserSession::getSessionId, sessionId)
                .eq(UserSession::getStatus, STATUS_NORMAL);
        return Math.toIntExact(userSessionMapper.selectCount(countWrapper));
    }

    /**
     * Assembles a UserSession and a Session into a UserGroupDTO
     */
    private UserGroupDTO convertToDTO(UserSession userSession, Session session,
                                      Map<Long, Long> creatorMap,
                                      Map<Long, Integer> memberCountMap) {
        UserGroupDTO dto = new UserGroupDTO();

        if (session != null) {
            dto.setSessionId(String.valueOf(session.getSessionId()));
            dto.setSessionName(session.getName());
            dto.setAvatar(session.getAvatar());
        }

        // Set the owner id
        Long creatorId = creatorMap.get(userSession.getSessionId());
        dto.setCreatorId(creatorId != null ? String.valueOf(creatorId) : null);

        // Set the user's role
        dto.setRole(userSession.getRole());

        // Set the member count
        dto.setMemberCount(memberCountMap.getOrDefault(userSession.getSessionId(), 0));

        // Format the join time
        if (userSession.getCreatedTime() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT);
            dto.setCreatedTime(sdf.format(userSession.getCreatedTime()));
        }

        return dto;
    }
}
