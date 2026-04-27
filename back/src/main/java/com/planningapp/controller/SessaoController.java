package com.planningapp.controller;

import com.planningapp.service.SessaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sessoes")
public class SessaoController {

    @Autowired
    private SessaoService sessaoService;

    @GetMapping("/online")
    public List<String> getUsuariosOnline() {
        return sessaoService.getUsuariosOnline();
    }
}
