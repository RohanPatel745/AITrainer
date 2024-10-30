package com.test.test;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/info")
    public String info() {
        return "info";
    }

    @GetMapping("/track")
    public String track() {
        return "track";
    }

    @GetMapping("/pathfinding")
    public String pathfinding() {
        return "pathfinding";
    }

    @GetMapping("/maze/mazeSelector")
    public String mazeSelector() {
        return "maze/mazeSelector";
    }
    
    @GetMapping("/maze/mazeLevelOne")
    public String mazeLevelOne() {
        return "maze/mazeLevelOne";
    }

    @GetMapping("/maze/mazeLevelTwo")
    public String mazeLevelTwo() {
        return "maze/mazeLevelTwo";
    }

    @GetMapping("/maze/mazeLevelThree")
    public String mazeLevelThree() {
        return "maze/mazeLevelThree";
    }
    
    @GetMapping("/maze/mazeLevelFour")
    public String mazeLevelFour() {
        return "maze/mazeLevelFour";
    }
    
    @GetMapping("/maze/mazeLevelFive")
    public String mazeLevelFive() {
        return "maze/mazeLevelFive";
    }
}