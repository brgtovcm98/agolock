package com.seu.seustock.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.seu.seustock.model.dto.BoxDTO;
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
class BoxMapperTest {

  @Autowired private BoxMapper boxMapper;

  @Autowired private ShelfMapper shelfMapper;

  @Autowired private SpaceMapper spaceMapper;

  @Autowired private UserMapper userMapper;

  private Long shelfId;

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

    ShelfDTO shelf = new ShelfDTO();
    shelf.setSpaceId(space.getId());
    shelf.setName("A선반");
    shelfMapper.insertShelf(shelf);
    shelfId = shelf.getId();
  }

  private BoxDTO buildBox(String name) {
    BoxDTO box = new BoxDTO();
    box.setShelfId(shelfId);
    box.setName(name);
    return box;
  }

  @Test
  void insertBox_thenFindById() {
    BoxDTO box = buildBox("1번박스");
    boxMapper.insertBox(box);

    Optional<BoxDTO> found = boxMapper.findById(box.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isNotNull();
    assertThat(found.get().getExternalId()).isNotNull();
    assertThat(found.get().getName()).isEqualTo("1번박스");
    assertThat(found.get().getShelfId()).isEqualTo(shelfId);
  }

  @Test
  void findById_notFound_returnsEmpty() {
    Optional<BoxDTO> found = boxMapper.findById(999L);
    assertThat(found).isEmpty();
  }

  @Test
  void findByShelfId() {
    boxMapper.insertBox(buildBox("1번박스"));
    boxMapper.insertBox(buildBox("2번박스"));

    List<BoxDTO> boxes = boxMapper.findByShelfId(shelfId);

    assertThat(boxes).hasSize(2);
    assertThat(boxes).extracting(BoxDTO::getName).containsExactlyInAnyOrder("1번박스", "2번박스");
  }

  @Test
  void updateBox() {
    BoxDTO box = buildBox("구박스");
    boxMapper.insertBox(box);
    box.setName("신박스");

    boxMapper.updateBox(box);

    Optional<BoxDTO> found = boxMapper.findById(box.getId());
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("신박스");
  }

  @Test
  void deleteById() {
    BoxDTO box = buildBox("삭제박스");
    boxMapper.insertBox(box);

    boxMapper.deleteById(box.getId());

    assertThat(boxMapper.findById(box.getId())).isEmpty();
  }
}
