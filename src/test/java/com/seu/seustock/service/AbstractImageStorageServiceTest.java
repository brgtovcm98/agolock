package com.seu.seustock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.seu.seustock.mapper.ImageMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.UserDTO;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class AbstractImageStorageServiceTest {

  @Test
  void store_rejectsSpoofedImageBeforeWritingToStorage() {
    TestImageStorageService service =
        new TestImageStorageService(
            mock(ImageMapper.class),
            mock(UserMapper.class),
            new ImageFileValidator(mockMessageSource()));
    UserDTO owner = new UserDTO();
    owner.setId(1L);
    MultipartFile file =
        new MockMultipartFile(
            "imageFile", "spoof.jpg", "image/jpeg", new byte[] {0x01, 0x02, 0x03});

    assertThatThrownBy(() -> service.store(file, owner, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("지원하지 않는 이미지 형식입니다.");

    assertThat(service.writeCalled).isFalse();
  }

  private static MessageSource mockMessageSource() {
    MessageSource ms = mock(MessageSource.class);
    when(ms.getMessage(any(), any(), any())).thenReturn("지원하지 않는 이미지 형식입니다.");
    return ms;
  }

  private static class TestImageStorageService extends AbstractImageStorageService {

    private boolean writeCalled;

    private TestImageStorageService(
        ImageMapper imageMapper, UserMapper userMapper, ImageFileValidator imageFileValidator) {
      super(imageMapper, userMapper, imageFileValidator);
    }

    @Override
    protected String writeToPhysicalStorage(
        MultipartFile file,
        UserDTO owner,
        String contentType,
        String originalFilename,
        String extension) {
      writeCalled = true;
      return "stored.jpg";
    }

    @Override
    public org.springframework.core.io.Resource load(com.seu.seustock.model.dto.ImageDTO image) {
      throw new UnsupportedOperationException();
    }
  }
}
