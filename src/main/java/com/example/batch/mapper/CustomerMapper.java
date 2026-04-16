package com.example.batch.mapper;

import com.example.batch.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface CustomerMapper {
    // 파티셔닝 조회를 위해 Map 파라미터를 사용
    List<Customer> findByIdRange(Map<String, Object> params);

    void updateStatus(Long id);
}
