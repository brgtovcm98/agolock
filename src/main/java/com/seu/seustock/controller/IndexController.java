package com.seu.seustock.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class IndexController {

  @GetMapping("/")
  public String index() {
    return "index";
  }

  @GetMapping("/empty")
  @ResponseBody
  public String empty() {
    return "<div id=\"modal\"></div>";
  }
}
