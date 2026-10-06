package mss.url.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RestController 
public class UserController {
    

    @GetMapping("Signin")
    public String Signin(@RequestParam String username, @RequestParam String password) {
        return new String();
    }

    @GetMapping("Signout")
    public String Signout(@RequestParam String userId) {
        return new String();
    }
    
    @PostMapping("SignUp")
    public String SignUp(@RequestBody String username, @RequestBody String password) {
        return new String();
    }
    

    /*
    *We are not liable for any imput mistake from the user, thus we didn t put any modifications, YDK FIH
    */
    
}
