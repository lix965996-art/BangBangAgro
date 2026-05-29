package com.farmland.intel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farmland.intel.entity.InventoryOutbound;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 物资出库记录Mapper
 */
@Mapper
public interface InventoryOutboundMapper extends BaseMapper<InventoryOutbound> {
    
    /**
     * 计算指定时间范围内的日均消耗量
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) / GREATEST(DATEDIFF(#{endTime}, #{startTime}), 1) " +
            "FROM inventory_outbound " +
            "WHERE inventory_id = #{inventoryId} AND outbound_time &gt;= #{startTime} AND outbound_time &lt;= #{endTime}")
    Double calculateDailyConsumption(@Param("inventoryId") Integer inventoryId,
                                     @Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 批量计算多个库存的日均消耗量
     * @return List of maps with keys: inventory_id, daily_consumption
     */
    @Select("<script>" +
            "SELECT inventory_id, COALESCE(SUM(quantity), 0) / GREATEST(DATEDIFF(#{endTime}, #{startTime}), 1) AS daily_consumption " +
            "FROM inventory_outbound " +
            "WHERE inventory_id IN " +
            "<foreach item='id' collection='inventoryIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " AND outbound_time &gt;= #{startTime} AND outbound_time &lt;= #{endTime} " +
            "GROUP BY inventory_id" +
            "</script>")
    List<Map<String, Object>> calculateDailyConsumptionBatch(@Param("inventoryIds") List<Integer> inventoryIds,
                                                              @Param("startTime") LocalDateTime startTime,
                                                              @Param("endTime") LocalDateTime endTime);
}

