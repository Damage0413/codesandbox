package com.oj.codesandbox.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.oj.codesandbox.JavaDockerCodeSandbox;
import com.oj.codesandbox.PythonDockerCodeSandbox;
import com.oj.codesandbox.model.ExecuteCodeRequest;
import com.oj.codesandbox.model.ExecuteCodeResponse;

@RestController("/")
public class MainController {

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";

    @Autowired
    private JavaDockerCodeSandbox javaDockerCodeSandbox;

    @Autowired
    private PythonDockerCodeSandbox pythonDockerCodeSandbox;

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request,
            HttpServletResponse response) {
        // 基本的认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        String language = executeCodeRequest.getLanguage();
        if (language == "java") {
            return javaDockerCodeSandbox.executeCode(executeCodeRequest);
        } else if (language == "python") {
            return pythonDockerCodeSandbox.executeCode(executeCodeRequest);
        } else {
            throw new RuntimeException("编程语言不符合要求");
        }
    }
}