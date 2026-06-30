package com.example.personal_blog;

import com.example.personal_blog.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PersonalBlogApplicationTests {


    @Test
    void register(){
        User user=new User();
        user.setUsername("Alex");
        user.setEmail("example@gamil.com");
        user.setPassword("abcd1234");

    }
}
