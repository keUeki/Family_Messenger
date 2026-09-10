package com.shanyangcode.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyangcode.common.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * Message table mapper
 * <p>
 * The session list shows the last message of each session, so this mapper is read-only;
 * writing messages remains the responsibility of OfflineDataService.
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * Loads the last message of each of the given sessions in one query
     *
     * @param sessionIds the session ids; the caller must ensure this is not empty
     * @return the most recent message of each session
     */
    @Select("""
            <script>
            SELECT message_id, sender_id, session_id, type, content, reply_id, session_type, created_time, updated_time
            FROM (
                SELECT m.*,
                       ROW_NUMBER() OVER (PARTITION BY m.session_id ORDER BY m.created_time DESC, m.message_id DESC) AS rn
                FROM `message` m
                WHERE m.session_id IN
                <foreach collection="sessionIds" item="sessionId" open="(" separator="," close=")">
                    #{sessionId}
                </foreach>
            ) ranked
            WHERE ranked.rn = 1
            </script>
            """)
    List<Message> selectLatestBySessionIds(@Param("sessionIds") Collection<Long> sessionIds);
}