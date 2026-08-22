package com.blog.site;

import com.blog.site.dto.HomeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/home")
public class PublicHomeController {
    private final HomeQueryService homeQueryService;

    public PublicHomeController(HomeQueryService homeQueryService) {
        this.homeQueryService = homeQueryService;
    }

    @GetMapping
    public HomeResponse getHome() {
        return homeQueryService.getHome();
    }
}
