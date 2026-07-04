package com.seu.seustock.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.seu.seustock.model.dto.UserDTO;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema-test.sql")
class UserMapperTest {

  @Autowired private UserMapper userMapper;

  private UserDTO buildUser(String email) {
    UserDTO user = new UserDTO();
    user.setEmail(email);
    user.setNickname("tester");
    user.setPassword("password");
    return user;
  }

  @Test
  void insertUser_thenFindByEmail() {
    UserDTO user = buildUser("alice@test.com");
    userMapper.insertUser(user);

    Optional<UserDTO> found = userMapper.findByEmail("alice@test.com");

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isNotNull();
    assertThat(found.get().getExternalId()).isNotNull();
    assertThat(found.get().getEmail()).isEqualTo("alice@test.com");
    assertThat(found.get().getNickname()).isEqualTo("tester");
  }

  @Test
  void findByEmail_notFound_returnsEmpty() {
    Optional<UserDTO> found = userMapper.findByEmail("nobody@test.com");
    assertThat(found).isEmpty();
  }

  @Test
  void findById() {
    UserDTO user = buildUser("dave@test.com");
    userMapper.insertUser(user);

    Optional<UserDTO> found = userMapper.findById(user.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getEmail()).isEqualTo("dave@test.com");
  }

  @Test
  void updateNickname() {
    UserDTO user = buildUser("eve@test.com");
    userMapper.insertUser(user);
    user.setNickname("newNick");

    userMapper.updateNickname(user);

    Optional<UserDTO> found = userMapper.findByEmail("eve@test.com");
    assertThat(found).isPresent();
    assertThat(found.get().getNickname()).isEqualTo("newNick");
  }

  @Test
  void updatePassword() {
    UserDTO user = buildUser("bob@test.com");
    userMapper.insertUser(user);
    user.setPassword("newPassword");

    userMapper.updatePassword(user);

    Optional<UserDTO> found = userMapper.findByEmail("bob@test.com");
    assertThat(found).isPresent();
    assertThat(found.get().getPassword()).isEqualTo("newPassword");
  }

  @Test
  void deleteById() {
    UserDTO user = buildUser("charlie@test.com");
    userMapper.insertUser(user);

    userMapper.deleteById(user.getId());

    assertThat(userMapper.findByEmail("charlie@test.com")).isEmpty();
  }
}
