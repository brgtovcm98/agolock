package com.seu.seustock.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.seu.seustock.model.dto.ImageDTO;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.StockDTO;
import com.seu.seustock.model.dto.UserDTO;
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
class ImageMapperTest {

  @Autowired private ImageMapper imageMapper;

  @Autowired private ItemImageMapper itemImageMapper;

  @Autowired private StockImageMapper stockImageMapper;

  @Autowired private ItemMapper itemMapper;

  @Autowired private StockMapper stockMapper;

  @Autowired private SpaceMapper spaceMapper;

  @Autowired private UserMapper userMapper;

  private UserDTO user;
  private ItemDTO item;
  private StockDTO stock;

  @BeforeEach
  void setUp() {
    user = new UserDTO();
    user.setEmail("image-user@test.com");
    user.setNickname("image-user");
    user.setPassword("password");
    userMapper.insertUser(user);

    item = new ItemDTO();
    item.setUserId(user.getId());
    item.setName("카메라");
    itemMapper.insertItem(item);

    var space = new com.seu.seustock.model.dto.SpaceDTO();
    space.setUserId(user.getId());
    space.setName("창고");
    spaceMapper.insertSpace(space);

    stock = new StockDTO();
    stock.setItemId(item.getId());
    stock.setSpaceId(space.getId());
    stockMapper.insertStock(stock);
  }

  @Test
  void insertImage_thenFindByExternalId() {
    ImageDTO image = buildImage("camera.jpg");

    imageMapper.insertImage(image);

    Optional<ImageDTO> found =
        imageMapper.findByExternalId(
            imageMapper.findById(image.getId()).orElseThrow().getExternalId());

    assertThat(found).isPresent();
    assertThat(found.get().getUserId()).isEqualTo(user.getId());
    assertThat(found.get().getStoragePath()).isEqualTo("/tmp/camera.jpg");
    assertThat(found.get().getContentType()).isEqualTo("image/jpeg");
  }

  @Test
  void findPrimaryByItemId_returnsPrimaryImage() {
    ImageDTO image = buildImage("item.jpg");
    imageMapper.insertImage(image);
    itemImageMapper.insertItemImage(item.getId(), image.getId(), 0, true);

    Optional<ImageDTO> found = imageMapper.findPrimaryByItemId(item.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getOriginalFilename()).isEqualTo("item.jpg");
  }

  @Test
  void findPrimaryByStockId_returnsPrimaryImage() {
    ImageDTO image = buildImage("stock.jpg");
    imageMapper.insertImage(image);
    stockImageMapper.insertStockImage(stock.getId(), image.getId(), 0, true);

    Optional<ImageDTO> found = imageMapper.findPrimaryByStockId(stock.getId());

    assertThat(found).isPresent();
    assertThat(found.get().getOriginalFilename()).isEqualTo("stock.jpg");
  }

  @Test
  void findByUserIdAndContentHash_returnsMatchingImage() {
    ImageDTO image = buildImage("hash-test.jpg");
    image.setContentHash("abc123hash");
    imageMapper.insertImage(image);

    Optional<ImageDTO> found = imageMapper.findByUserIdAndContentHash(user.getId(), "abc123hash");

    assertThat(found).isPresent();
    assertThat(found.get().getOriginalFilename()).isEqualTo("hash-test.jpg");
  }

  @Test
  void findByUserIdAndContentHash_notFound_returnsEmpty() {
    assertThat(imageMapper.findByUserIdAndContentHash(user.getId(), "nonexistent")).isEmpty();
  }

  @Test
  void findPrimaryByItemId_noImage_returnsEmpty() {
    assertThat(imageMapper.findPrimaryByItemId(item.getId())).isEmpty();
  }

  @Test
  void findPrimaryByStockId_noImage_returnsEmpty() {
    assertThat(imageMapper.findPrimaryByStockId(stock.getId())).isEmpty();
  }

  private ImageDTO buildImage(String filename) {
    ImageDTO image = new ImageDTO();
    image.setUserId(user.getId());
    image.setStoragePath("/tmp/" + filename);
    image.setOriginalFilename(filename);
    image.setContentType("image/jpeg");
    image.setSizeBytes(128L);
    return image;
  }
}
