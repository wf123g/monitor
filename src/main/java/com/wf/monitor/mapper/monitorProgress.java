package com.wf.monitor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface monitorProgress {

    /** 返回 Excel 显示行号；进度记录未初始化时返回 null。 */
    @Select("SELECT read_row_number FROM monitor_progress WHERE id = 1")
    Integer selectLastRows();
@Update("update monitor_progress set read_row_number=read_row_number+#{count} where id=1 ")
    void updateReadRows(int count);
}
