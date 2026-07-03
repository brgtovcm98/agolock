package com.seu.seustock.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.seu.seustock.mapper.BoxMapper;
import com.seu.seustock.mapper.ShelfMapper;
import com.seu.seustock.mapper.SpaceMapper;
import com.seu.seustock.mapper.UserMapper;
import com.seu.seustock.model.dto.BoxDTO;
import com.seu.seustock.model.dto.ShelfDTO;
import com.seu.seustock.model.dto.SpaceDTO;
import com.seu.seustock.model.dto.UserDTO;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QrControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private UserMapper userMapper;

  @Autowired private SpaceMapper spaceMapper;

  @Autowired private ShelfMapper shelfMapper;

  @Autowired private BoxMapper boxMapper;

  @Autowired private PasswordEncoder passwordEncoder;

  private UserDTO testUser;
  private SpaceDTO testSpace;
  private ShelfDTO testShelf;
  private BoxDTO testBox;

  @BeforeEach
  void setUp() {
    testUser = new UserDTO();
    testUser.setEmail("testuser@test.com");
    testUser.setNickname("testuser");
    testUser.setPassword(passwordEncoder.encode("password"));
    userMapper.insertUser(testUser);
    testUser = userMapper.findByEmail("testuser@test.com").orElseThrow();

    testSpace = new SpaceDTO();
    testSpace.setUserId(testUser.getId());
    testSpace.setName("Test Space");
    spaceMapper.insertSpace(testSpace);
    testSpace = spaceMapper.findByUserId(testUser.getId()).get(0);

    testShelf = new ShelfDTO();
    testShelf.setSpaceId(testSpace.getId());
    testShelf.setName("Test Shelf");
    shelfMapper.insertShelf(testShelf);
    testShelf = shelfMapper.findBySpaceId(testSpace.getId()).get(0);

    testBox = new BoxDTO();
    testBox.setShelfId(testShelf.getId());
    testBox.setName("Test Box");
    boxMapper.insertBox(testBox);
    testBox = boxMapper.findByShelfId(testShelf.getId()).get(0);
  }

  @Test
  @DisplayName("박스 QR 스캔 시 해당 재고 페이지로 리다이렉트 (로그인 상태)")
  void scanBoxRedirect() throws Exception {
    mockMvc
        .perform(
            get("/qr/boxes/" + testBox.getExternalId())
                .with(user("testuser@test.com").roles("USER")))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            redirectedUrl(
                String.format(
                    "/spaces/%s/shelves/%s/boxes/%s/stocks",
                    testSpace.getExternalId(),
                    testShelf.getExternalId(),
                    testBox.getExternalId())));
  }

  @Test
  @DisplayName("로그인하지 않은 상태에서 박스 QR 스캔 시 로그인 페이지로 리다이렉트되고 saved request가 세션에 저장된다")
  void scanBoxUnauthenticated() throws Exception {
    String qrPath = "/qr/boxes/" + testBox.getExternalId();

    MvcResult result =
        mockMvc
            .perform(get(qrPath))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"))
            .andReturn();

    HttpSession session = result.getRequest().getSession(false);
    assertThat(session).isNotNull();
    SavedRequest savedRequest =
        (SavedRequest) session.getAttribute("SPRING_SECURITY_SAVED_REQUEST");
    assertThat(savedRequest).isNotNull();
    assertThat(savedRequest.getRedirectUrl()).contains(qrPath);
  }

  @Test
  @DisplayName("QR 스캔 후 로그인하면 saved request 덕분에 원래 박스 재고 페이지로 되돌아간다")
  void scanBoxThenLoginRestoresOriginalDestination() throws Exception {
    String qrPath = "/qr/boxes/" + testBox.getExternalId();

    MvcResult scanResult =
        mockMvc.perform(get(qrPath)).andExpect(status().is3xxRedirection()).andReturn();

    org.springframework.mock.web.MockHttpSession session =
        (org.springframework.mock.web.MockHttpSession) scanResult.getRequest().getSession(false);
    assertThat(session).isNotNull();

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/login")
                    .session(session)
                    .param("email", "testuser@test.com")
                    .param("password", "password")
                    .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    String location = loginResult.getResponse().getRedirectedUrl();
    assertThat(location).contains(qrPath);
  }

  @Test
  @DisplayName("미인증 상태에서 /api/qr/generate 호출 시 로그인 페이지로 리다이렉트된다")
  void generateQr_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc
        .perform(
            get("/api/qr/generate")
                .param("content", "https://example.com")
                .accept(MediaType.IMAGE_PNG_VALUE))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }
}
