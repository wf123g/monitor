package com.wf.monitor.mapper;

import com.wf.monitor.entity.BugNotifyRecord;
import com.wf.monitor.vo.BugMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;


@Mapper
public interface bugNotifyRecord {
    @Insert("""
    INSERT INTO bug_notify_record (
        excel_row_number,
        problem_description,
        owner_name,
        notify_status,
        waiting_reason,
        developer_id
    ) VALUES (
        #{excelRowNumber},
        #{problemDescription},
        #{ownerName},
        #{notifyStatus},
        #{waitingReason},
        #{developerId}
    )
    """)
    void insertBug(BugNotifyRecord bnr);
@Select("select excel_row_number from bug_notify_record where notify_status=#{status}")
List<Integer> selectRowsByStatus(int status);
    @Update("""
    UPDATE bug_notify_record
    SET owner_name = #{ownerName},
        developer_id = #{developerId},
        problem_description = #{problemDescription},
        notify_status = #{notifyStatus},
        waiting_reason = #{waitingReason}
    WHERE excel_row_number = #{excelRowNumber}
      AND notify_status = 0
    """)
      void updateByBnr(BugNotifyRecord bnr);
    @Select("select excel_row_number,problem_description,dingtalk_user_id,mobile from developer_mapping dm join bug_notify_record bnr where dm.id=bnr.developer_id and bnr.notify_status = #{i} and dm.enabled=1 ")

    List<BugMessage> selectBugByStatus(int i);
@Update("update bug_notify_record set notify_status=#{status} where excel_row_number=#{rownumber}")
    void updateStatus(int rownumber, int status);
}
