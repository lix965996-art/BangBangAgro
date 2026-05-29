package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 
 * </p>
 *
 * @author 
 * @since 2023-02-22
 */
@Getter
@Setter
@Schema(description = "Statistic对象")
public class Statistic implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "农田名称")
    private String farm;

    @Schema(description = "面积")
    private String area;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "所属区县")
    private String district;

    @Schema(description = "作物名称")
    private String crop;

    @Schema(description = "数量")
    private Integer number;

    @Schema(description = "生长状态")
    private String state;

    @Schema(description = "温度（℃）")
    private BigDecimal temperature;

    @Schema(description = "空气湿度（%）")
    private Integer airhumidity;

    @Schema(description = "土壤湿度（%）")
    private Integer soilhumidity;

    @Schema(description = "CO2含量ppm")
    private Integer carbon;

    @Schema(description = "土壤PH值")
    private BigDecimal ph;

    @Schema(description = "光照强度(lux)")
    private Integer light;

    @Schema(description = "补光灯状态")
    private String filllight;

    @Schema(description = "摄像头状态")
    private String monitor;

    @Schema(description = "水泵状态")
    private String pump;

    @Schema(description = "农田负责人")
    private String keeper;

    @Schema(description = "中心经度")
    private BigDecimal centerLng;

    @Schema(description = "中心纬度")
    private BigDecimal centerLat;

    @Schema(description = "区域坐标JSON（多边形顶点数组）")
    private String coordinates;

}
