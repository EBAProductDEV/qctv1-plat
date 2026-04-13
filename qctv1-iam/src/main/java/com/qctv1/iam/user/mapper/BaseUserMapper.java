package com.qctv1.iam.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qctv1.iam.user.entity.BaseUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BaseUserMapper extends BaseMapper<BaseUser> {
}
