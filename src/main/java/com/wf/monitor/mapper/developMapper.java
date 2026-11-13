package com.wf.monitor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface developMapper {
    @Select("select id from developer_mapping where owner_name=#{name}")
     Long selectIdByName(String name);
}
