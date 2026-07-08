package com.seu.seustock.mapper;

import com.seu.seustock.model.dto.UserDTO;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
  void insertUser(UserDTO user);

  Optional<UserDTO> findById(Long id);

  Optional<UserDTO> findByEmail(String email);

  void updateNickname(UserDTO user);

  void updatePassword(UserDTO user);

  void updateTargetTotalStock(@Param("userId") Long userId, @Param("target") Integer target);

  void deleteById(Long id);
}
