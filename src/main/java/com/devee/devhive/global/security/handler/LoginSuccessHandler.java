package com.devee.devhive.global.security.handler;

import com.devee.devhive.domain.auth.dto.LoginUserDto;
import com.devee.devhive.domain.user.entity.User;
import com.devee.devhive.domain.user.repository.UserRepository;
import com.devee.devhive.global.security.dto.TokenDto;
import com.devee.devhive.global.entity.PrincipalDetails;
import com.devee.devhive.global.security.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

@Slf4j
@RequiredArgsConstructor
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final TokenService tokenService;
  private final UserRepository userRepository;

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) throws IOException {
    String email = extractUsername(authentication); // 인증 정보에서 Username(email) 추출

    String accessToken = tokenService.createAccessToken(email); // JwtService의 createAccessToken을 사용하여 AccessToken 발급
    String refreshToken = tokenService.createRefreshToken(); // JwtService의 createRefreshToken을 사용하여 RefreshToken 발급

    tokenService.sendAccessAndRefreshToken(response, accessToken, refreshToken); // 응답 헤더에 AccessToken, RefreshToken 실어서 응답

    userRepository.findByEmail(email)
        .ifPresent(user -> {
          user.updateRefreshToken(refreshToken);
          userRepository.saveAndFlush(user);
        });

    log.info("로그인에 성공하였습니다. 이메일 : {}", email);
    log.info("로그인에 성공하였습니다. AccessToken : {}", accessToken);

    User user = userRepository.findByEmail(email).orElse(null);

    TokenDto tokenDto = TokenDto.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .userDto(LoginUserDto.from(Objects.requireNonNull(user)))
        .build();

    String tokenJson = new ObjectMapper().writeValueAsString(tokenDto); // TokenDto 객체를 JSON 문자열로 변환

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setStatus(HttpServletResponse.SC_OK);
    response.getWriter().write(tokenJson); // JSON 문자열을 응답으로 보내기
  }

  private String extractUsername(Authentication authentication) {
    PrincipalDetails userDetails = (PrincipalDetails) authentication.getPrincipal();
    return userDetails.getUsername();
  }
}
