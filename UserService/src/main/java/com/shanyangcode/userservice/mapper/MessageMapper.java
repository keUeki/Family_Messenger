package com.shanyangcode.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shanyangcode.common.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 消息表 Mapper
 * <p>
 * 会话列表需要展示每个会话的最后一条消息，因此这里只提供读取能力，
 * 消息的写入依旧由 OfflineDataService 负责。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 批量查询若干会话各自的最后一条消息
     *
     * @param sessionIds 会话 ID 集合，调用方需保证非空
     * @return 每个会话最新的一条消息
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