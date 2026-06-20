package com.seu.seustock.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.UserDTO;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
class ShelfMapperTest {

  @Autowired private ShelfMapper shelfMapper;

  @Autowired private SpaceMapper spaceMapper;

  @Autowired private UserMapper userMapper;

  private Long spaceId;

  @BeforeEach
  void setUp() {
    UserDTO user = new UserDTO();
    user.setEmail("testuser@test.com");
    user.setNickname("testuser");
    user.setPassword("password");
    userMapper.insertUser(user);

    SpaceDTO space = new SpaceDTO();
    space.setUserId(user.getId());
    space.setName("창고");
    spaceMapper.insertSpace(space);
    spaceId = space.getId();
  }

  private ShelfDTO buildShelf(String name) {
    ShelfDTO shelf = new ShelfDTO();
    shelf.setSpaceId(spaceId);
    shelf.setName(name);
    return shelf;
  }

  @Test
  void insertShelf_thenFindById() {
    ShelfDTO shelf = buildShelf("A선반");
    shelfMapper.insertShelf(shelf);

    Optional<ShelfDTO> found = shelfMapper.findById(shelf.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isNotNull();
    assertThat(found.get().getExternalId()).isNotNull();
    assertThat(found.get().getName()).isEqualTo("A선반");
    assertThat(found.get().getSpaceId()).isEqualTo(spaceId);
  }

  @Test
  void findById_notFound_returnsEmpty() {
    Optional<ShelfDTO> found = shelfMapper.findById(999L);
    assertThat(found).isEmpty();
  }

  @Test
  void findBySpaceId() {
    shelfMapper.insertShelf(buildShelf("A선반"));
    shelfMapper.insertShelf(buildShelf("B선반"));

    List<ShelfDTO> shelves = shelfMapper.findBySpaceId(spaceId);

    assertThat(shelves).hasSize(2);
    assertThat(shelves).extracting(ShelfDTO::getName).containsExactlyInAnyOrder("A선반", "B선반");
  }

  @Test
  void updateShelf() {
    ShelfDTO shelf = buildShelf("구선반");
    shelfMapper.insertShelf(shelf);
    shelf.setName("신선반");

    shelfMapper.updateShelf(shelf);

    Optional<ShelfDTO> found = shelfMapper.findById(shelf.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("신선반");
  }

  @Test
  void deleteById() {
    ShelfDTO shelf = buildShelf("삭제선반");
    shelfMapper.insertShelf(shelf);

    shelfMapper.deleteById(shelf.getId());

    assertThat(shelfMapper.findById(shelf.getId())).isEmpty();
  }
}
