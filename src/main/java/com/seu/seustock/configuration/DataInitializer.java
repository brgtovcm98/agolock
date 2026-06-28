package com.seu.seustock.configuration;

import com.seu.seustock.mapper.BoxMapper;
import com.seu.seustock.mapper.ItemImageMapper;
import com.seu.seustock.mapper.ItemMapper;
import com.seu.seustock.mapper.ShelfMapper;
import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.mapper.StockImageMapper;
import com.seu.seustock.mapper.StockMapper;
import com.seu.seustock.mapper.StockTransactionMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.ImageDTO;
import com.seu.seustock.model.dto.ItemDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.StockDTO;
import com.seu.seustock.model.dto.StockTransactionDTO;
import com.seu.seustock.model.dto.UserDTO;
import com.seu.seustock.model.enumeration.TransactionMemoMaster;
import com.seu.seustock.model.enumeration.TransactionType;
import com.seu.seustock.service.ImageStorageService;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@ConditionalOnProperty(
    name = "seustock.datainit.enabled",
    havingValue = "true",
    matchIfMissing = false)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

  @Value("${seustock.datainit.seed-email:}")
  private String seedEmail;

  @Value("${seustock.datainit.seed-password:}")
  private String seedPassword;

  private static final int SPACE_COUNT = 25;
  private static final int SHELF_COUNT = 5;
  private static final int BOX_COUNT = 5;
  private static final int ITEM_COUNT = 25;
  private static final int STOCKED_ITEM_COUNT = 5;
  private static final int STOCKS_PER_ITEM = 5;
  private static final int ITEMS_WITH_IMAGES = 20;
  private static final int STOCKS_WITH_IMAGES = 10;

  private static final List<String> SAMPLE_IMAGE_FILENAMES =
      List.of("tissue.png", "driver.png", "mug.png", "keyboard.png");

  private static final List<String> ITEM_NAMES =
      List.of(
          "휴지", "드라이버", "머그컵", "키보드", "마우스", "노트북", "펜", "노트", "가위", "테이프", "스테이플러", "USB 케이블",
          "이어폰", "충전기", "책", "시계", "우산", "물병", "안경", "지갑", "리모컨", "건전지", "전구", "소켓", "공구함");

  // 위치 패턴: { shelfIndex, boxIndex }. -1 = null
  private static final int[][] STOCK_LOCATION_PATTERN = {
    {-1, -1},
    {-1, -1},
    {0, -1},
    {0, 0},
    {0, 1}
  };

  private final UserMapper userMapper;
  private final SpaceMapper spaceMapper;
  private final ShelfMapper shelfMapper;
  private final BoxMapper boxMapper;
  private final ItemMapper itemMapper;
  private final StockMapper stockMapper;
  private final StockTransactionMapper stockTransactionMapper;
  private final ItemImageMapper itemImageMapper;
  private final StockImageMapper stockImageMapper;
  private final PasswordEncoder passwordEncoder;
  private final ImageStorageService imageStorageService;
  private final ResourceLoader resourceLoader;

  @Override
  @Transactional
  public void run(String... args) throws Exception {
    if (seedEmail == null
        || seedEmail.isBlank()
        || seedPassword == null
        || seedPassword.isBlank()) {
      log.info("[DataInitializer] 시드 계정 정보가 설정되지 않아 초기 데이터 생성을 건너뜁니다.");
      return;
    }

    if (userMapper.findByEmail(seedEmail).isPresent()) {
      log.info("[DataInitializer] '{}' 사용자 존재 - 초기 데이터 생성을 건너뜁니다.", seedEmail);
      return;
    }

    log.info("[DataInitializer] '{}' 사용자가 없어 초기 더미 데이터를 생성합니다.", seedEmail);

    UserDTO user = createUser();
    List<SpaceDTO> spaces = createSpaces(user);
    SpaceDTO firstSpace = spaces.get(0);
    List<ShelfDTO> shelves = createShelves(firstSpace);
    ShelfDTO firstShelf = shelves.get(0);
    List<BoxDTO> boxes = createBoxes(firstShelf);
    List<ItemDTO> items = createItems(user);
    List<ImageDTO> images = uploadSampleImages(user);
    attachItemImages(items, images);
    List<StockDTO> stocks = createStocksAndTransactions(items, firstSpace, shelves, boxes);
    attachStockImages(stocks, images);

    log.info("[DataInitializer] 초기 더미 데이터 생성 완료.");
  }

  private UserDTO createUser() {
    UserDTO user = new UserDTO();
    user.setEmail(seedEmail);
    user.setNickname(seedEmail.split("@")[0]);
    user.setPassword(passwordEncoder.encode(seedPassword));
    userMapper.insertUser(user);
    return userMapper.findByEmail(seedEmail).orElseThrow();
  }

  private List<SpaceDTO> createSpaces(UserDTO user) {
    List<SpaceDTO> list = new ArrayList<>(SPACE_COUNT);
    for (int i = 1; i <= SPACE_COUNT; i++) {
      SpaceDTO space = new SpaceDTO();
      space.setUserId(user.getId());
      space.setName("공간 " + i);
      spaceMapper.insertSpace(space);
      list.add(space);
    }
    return list;
  }

  private List<ShelfDTO> createShelves(SpaceDTO space) {
    List<ShelfDTO> list = new ArrayList<>(SHELF_COUNT);
    for (int i = 1; i <= SHELF_COUNT; i++) {
      ShelfDTO shelf = new ShelfDTO();
      shelf.setSpaceId(space.getId());
      shelf.setName("선반 " + i);
      shelfMapper.insertShelf(shelf);
      list.add(shelf);
    }
    return list;
  }

  private List<BoxDTO> createBoxes(ShelfDTO shelf) {
    List<BoxDTO> list = new ArrayList<>(BOX_COUNT);
    for (int i = 1; i <= BOX_COUNT; i++) {
      BoxDTO box = new BoxDTO();
      box.setShelfId(shelf.getId());
      box.setName("박스 " + i);
      boxMapper.insertBox(box);
      list.add(box);
    }
    return list;
  }

  private List<ItemDTO> createItems(UserDTO user) {
    List<ItemDTO> list = new ArrayList<>(ITEM_COUNT);
    for (int i = 0; i < ITEM_COUNT; i++) {
      String name = ITEM_NAMES.get(i);
      ItemDTO item = new ItemDTO();
      item.setUserId(user.getId());
      item.setName(name);
      item.setDescription("샘플 데이터 - " + name);
      itemMapper.insertItem(item);
      list.add(item);
    }
    return list;
  }

  private List<ImageDTO> uploadSampleImages(UserDTO owner)
      throws IOException, NoSuchAlgorithmException {
    List<ImageDTO> list = new ArrayList<>(SAMPLE_IMAGE_FILENAMES.size());
    for (String filename : SAMPLE_IMAGE_FILENAMES) {
      Resource resource = resourceLoader.getResource("classpath:static/sample/" + filename);
      byte[] bytes;
      try (InputStream is = resource.getInputStream()) {
        bytes = is.readAllBytes();
      }
      String contentHash = sha256Hex(bytes);
      MultipartFile multipartFile = new ByteArrayMultipartFile(filename, "image/png", bytes);
      ImageDTO image = imageStorageService.store(multipartFile, owner, contentHash);
      list.add(image);
    }
    return list;
  }

  private void attachItemImages(List<ItemDTO> items, List<ImageDTO> images) {
    List<Integer> selected = pickIndices(items.size(), ITEMS_WITH_IMAGES, 42L);
    for (Integer idx : selected) {
      ItemDTO item = items.get(idx);
      ImageDTO image = images.get(idx % images.size());
      itemImageMapper.insertItemImage(item.getId(), image.getId(), 0, true);
    }
  }

  private List<StockDTO> createStocksAndTransactions(
      List<ItemDTO> items, SpaceDTO space, List<ShelfDTO> shelves, List<BoxDTO> boxes) {
    List<StockDTO> stocks = new ArrayList<>(STOCKED_ITEM_COUNT * STOCKS_PER_ITEM);
    for (int i = 0; i < STOCKED_ITEM_COUNT; i++) {
      ItemDTO item = items.get(i);
      for (int j = 0; j < STOCKS_PER_ITEM; j++) {
        int[] loc = STOCK_LOCATION_PATTERN[j];
        Long shelfId = loc[0] >= 0 ? shelves.get(loc[0]).getId() : null;
        Long boxId = loc[1] >= 0 ? boxes.get(loc[1]).getId() : null;

        StockDTO stock = new StockDTO();
        stock.setItemId(item.getId());
        stock.setSpaceId(space.getId());
        stock.setShelfId(shelfId);
        stock.setBoxId(boxId);
        stock.setSerialNumber("SN-" + (i + 1) + "-" + (j + 1));
        stock.setLotNumber("LOT-" + (i + 1));
        stockMapper.insertStock(stock);

        StockTransactionDTO tx = new StockTransactionDTO();
        tx.setStockId(stock.getId());
        tx.setTransactionType(TransactionType.IN);
        tx.setToSpaceId(stock.getSpaceId());
        tx.setToShelfId(stock.getShelfId());
        tx.setToBoxId(stock.getBoxId());
        tx.setMemo(TransactionMemoMaster.PURCHASE_IN.getMessageKey());
        stockTransactionMapper.insertTransaction(tx);

        stocks.add(stock);
      }
    }
    return stocks;
  }

  private void attachStockImages(List<StockDTO> stocks, List<ImageDTO> images) {
    List<Integer> selected = pickIndices(stocks.size(), STOCKS_WITH_IMAGES, 43L);
    for (Integer idx : selected) {
      StockDTO stock = stocks.get(idx);
      ImageDTO image = images.get(idx % images.size());
      stockImageMapper.insertStockImage(stock.getId(), image.getId(), 0, true);
    }
  }

  private static List<Integer> pickIndices(int total, int count, long seed) {
    List<Integer> indices = new ArrayList<>(total);
    for (int i = 0; i < total; i++) {
      indices.add(i);
    }
    Collections.shuffle(indices, new Random(seed));
    return new ArrayList<>(indices.subList(0, Math.min(count, total)));
  }

  private static String sha256Hex(byte[] bytes) throws NoSuchAlgorithmException {
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
    StringBuilder sb = new StringBuilder(digest.length * 2);
    for (byte b : digest) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private static final class ByteArrayMultipartFile implements MultipartFile {
    private final String filename;
    private final String contentType;
    private final byte[] content;

    ByteArrayMultipartFile(String filename, String contentType, byte[] content) {
      this.filename = filename;
      this.contentType = contentType;
      this.content = content;
    }

    @Override
    public String getName() {
      return "file";
    }

    @Override
    public String getOriginalFilename() {
      return filename;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public boolean isEmpty() {
      return content.length == 0;
    }

    @Override
    public long getSize() {
      return content.length;
    }

    @Override
    public byte[] getBytes() {
      return content;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File dest) throws IOException {
      try (FileOutputStream out = new FileOutputStream(dest)) {
        out.write(content);
      }
    }

    @Override
    public void transferTo(Path dest) throws IOException {
      Files.write(dest, content);
    }
  }
}
