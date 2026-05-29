package com.farmland.intel.mapper;

import com.farmland.intel.entity.Dict;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface DictMapper {

    @Select("select name, value, type from sys_dict where type = #{type}")
    List<Dict> selectByType(@Param("type") String type);
}
