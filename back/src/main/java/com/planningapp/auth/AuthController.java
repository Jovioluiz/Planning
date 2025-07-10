package com.planningapp.auth;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planningapp.dto.LoginDTO;
import com.planningapp.entity.User;
import com.planningapp.entity.enums.TipoPerfil;
import com.planningapp.repository.UserRepository;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {
	
    @Autowired
    private UserRepository userRepository;
	
	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody LoginDTO login){
	    System.out.println("Recebido no login:");
	    System.out.println("Usuario: " + login.getUsuario());
	    System.out.println("Senha: " + login.getSenha());
	    
		String usuario = login.getUsuario().trim();
		String senha = login.getSenha().trim();
		String perfil = login.getPerfil();
		
		return userRepository.findByUsuario(usuario)
	            .filter(user -> user.getSenha().equals(senha))
	            .map(user -> ResponseEntity.ok(Map.of("success", true, "message", "Login realizado com sucesso", "perfil", user.getTipoPerfil())))
	            .orElseGet(() -> {
	            	User novoUsuario = new User();
	            	novoUsuario.setUsuario(usuario);
	            	novoUsuario.setSenha(senha);
	            	novoUsuario.setTipoPerfil(TipoPerfil.valueOf(perfil.toUpperCase()));
	            	
	            	userRepository.save(novoUsuario);
	            	
	            	return ResponseEntity.ok(Map.of("success", true, "message", "Login realizado com sucesso", "perfil", novoUsuario.getTipoPerfil()));
	            });
	}
}
