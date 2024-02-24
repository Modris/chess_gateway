# Chess_Gateway. A Puzzle piece for SlyChess project.

* Backend-For-Frontend Pattern (BFF). More info below.
  
* Reverse Proxy for serving Chess_Frontend and Chess_Manager.

* Connected to Keycloak Authorization server as an Oauth2 Client.

* Spring Security for endpoint protection.

# Main Dependencies 

* Spring Cloud Gateway. This makes the application **REACTIVE**. From community pushback the team was forced to develop Spring Cloud Gateway MVC. However as of 24.02.2024 it lacks a lot of features. For SlyChess Project [I needed WebSockets](https://github.com/spring-cloud/spring-cloud-gateway/pull/2949#issue-1703012276) which was not available for MVC version so i was forced to stick with Spring Cloud Gateway.
  
*  Oauth2 Client (who can keep a secret)
   
*  Spring Security (to protect endpoints)

# Backend-For-Frontend (BFF) Pattern.

All solutions stem from a problem. What is the problem that BFF pattern tries to solve? A security expert Philippe De Ryck says: 
["From a security perspective, it is virtually impossible to secure tokens in a frontend web application." And he recommends 
that developers rely on BFF pattern instead.](https://www.pingidentity.com/en/resources/blog/post/refresh-token-rotation-spa.html)

The BFF pattern requires that the a server-side application takes care of authorizing requests from the frontend. It will be an Oauth2 Client 
who can actually keep a secret (secret string code so the authorization server can make sure it's an Oauth2 Client). What differs from Regular Authorization 
Flow where the server exchanges the auhtorization code for access token is that we bind the access token (or ID token if OIDC) to a session cookie 
and all further requests to protected endpoints will be with this session cookie. Here is the flow:



<div align="center">
  <img src="/project_images/oidc_flow.png" alt="oidc">
</div>

# BFF key points 
* The server has to act as a reverse proxy to server the frontend. It protects endpoints with Spring Security.

* We change Session to a Session Cookie and CSRF header to a CSRF Cookie before sending them to the frontend.
  
* Since nowadays backend and frontend are seperate applications with their own build tools we have to change the configuration so the default 
answer is 401 unauthenticated instead of 302 redirect to Authorization Server. The main reason for not using redirects with SPAs is that you would run into Cross-
Origin Request Sharing (CORS) issues.

* Logout is a bit more complex. In General you would call Spring Server POST /logout endpoint and be done. But since we are authenticated inside the Authorization Server
  as well we have to do RP-Initiated Logout. And we don't want to get 302 redirected to Authorization Server logout endpoint (remeber SPA CORS issues). We will send HTTP 202 Status with Location Header. It will contain the logout URL for Authorization Server with which the frontend can then window.location.href and follow through to complete the logout.

# Chess_Gateway comments

* It's like a Reverse Proxy for Serving Chess_Frontend and Chess_Manager.

* Spring Cloud Gateway makes the application reactive. The SecurityWebFilterChain allows to serve Chess_frontend assets and certain endpoints for Chess_Manager and protects all others with Spring Security.

* Chess_Manager is a Resource Server and it has some protected endpoints. In application.yaml we need to convert the Session Cookie to an Authorization Header with Bearer Token. We can do this with a filter called TokenRelay.

* We Solve frontend CORS issues by serving the frontend from Chess_Gateway port. We Solve frontend CORS issues by not using 302 redirects (because of SPA) but by returning 401 unauthenticated for login. For logout we return 202 Status code with Location Header containing the URL which the frontend can follow with window.location.href.

 * @RestController method with @GetMapping("/user") which just returns the User username. It's a protected endpoint and requires the user to be authenticated. Otherwise we return 401.

# End 

That's about it. I don't have any tests because i'm a beginner to reactive programming in general. And the way i build projects is i start with something simple. But for reactive programming i started with something quite complex - Oauth2 and Spring Cloud Gateway. The Source Code for BFF pattern and other Oauth2 implementations in Spring can be found in [ch4mpy github](https://github.com/ch4mpy/spring-addons/tree/6.2.0/samples/tutorials). He has many tutorials on Oauth2 implementation - official and his own wrapper. He has spent a lot of time digging through Spring Documentation and easing the implementation of Oauth2 with Spring. I learned a lot from him. 
