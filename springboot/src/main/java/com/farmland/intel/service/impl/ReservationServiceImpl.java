package com.farmland.intel.service.impl;

import com.farmland.intel.entity.Reservation;
import com.farmland.intel.mapper.ReservationMapper;
import com.farmland.intel.service.IReservationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ReservationServiceImpl extends ServiceImpl<ReservationMapper, Reservation> implements IReservationService {
}
