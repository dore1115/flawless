package com.dsa.flawless.controller;

import com.dsa.flawless.service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * [공용 파일] 메인 페이지 재고관리
 * 카드에 필요한 최소한(ItemService 주입 + itemSummary 모델 속성 1개)만 추가.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class IndexController {

    private final ItemService itemService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("itemSummary", itemService.getDashboardSummary());
        return "index";
    }

}
