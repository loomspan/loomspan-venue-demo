package demo.annex;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** Deliberate local demo impersonation; this is not a login or identity-verification system. */
@Configuration
@EnableMethodSecurity(jsr250Enabled=true)
public class DemoSecurity {
    @Bean SecurityFilterChain demoChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf->csrf.disable())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.anyRequest().permitAll())
            .addFilterBefore(new IdentityFilter(),AnonymousAuthenticationFilter.class).build();
    }
    static class IdentityFilter extends OncePerRequestFilter {
        @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
            String user=request.getHeader("X-Annex-Demo-User");
            if(user==null) user="alex";
            if(!user.equals("alex")&&!user.equals("morgan")) {
                response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"message\":\"Choose the Alex or Morgan demo identity.\"}");return;
            }
            var context=SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of(new SimpleGrantedAuthority(user.equals("morgan")?"ROLE_MANAGER":"ROLE_COORDINATOR"))));
            SecurityContextHolder.setContext(context);
            try {chain.doFilter(request,response);} finally {SecurityContextHolder.clearContext();}
        }
    }
}
