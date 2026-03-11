package com.communitysport.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * 全局异常处理器（统一把后端异常转换成前端可消费的 JSON 响应）。
 *
 * <p>为什么需要它？
 * <p>1）前后端分离项目中，前端更希望拿到结构稳定的 JSON，而不是 HTML 错误页。
 * <p>2）不同异常（参数校验失败、找不到资源、权限不足、服务端错误）如果不统一处理，
 *    前端需要写大量“特判”逻辑，体验和维护都会变差。
 *
 * <p>本项目约定统一错误响应格式：
 * <pre>
 * { "code": 400, "msg": "Bad Request" }
 * </pre>
 *
 * <p>注意：这里只处理“异常到响应”的映射，不承担业务校验逻辑。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        // ResponseStatusException 是 Spring Web 中常用的“带 HTTP 状态码的异常”。
        // 例如：throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "xxx")
        // 我们把它的 status/reason 转成统一的 {code,msg} JSON。
        int code = ex.getStatusCode().value();
        String msg = ex.getReason();
        if (!StringUtils.hasText(msg)) {
            msg = ex.getStatusCode().toString();
        }
        return ResponseEntity.status(code).body(body(code, msg));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Map<String, Object>> handleValidation(Exception ex) {
        // 参数校验异常：
        // - MethodArgumentNotValidException：通常来自 @RequestBody + @Valid
        // - BindException：通常来自 @RequestParam/@ModelAttribute 的绑定校验
        //
        // 这里尽量把“具体字段的校验提示”透传给前端，用户体验更好。
        String msg = "Bad Request";
        if (ex instanceof MethodArgumentNotValidException manv) {
            if (manv.getBindingResult() != null && manv.getBindingResult().getFieldError() != null) {
                String m = manv.getBindingResult().getFieldError().getDefaultMessage();
                if (StringUtils.hasText(m)) {
                    msg = m;
                }
            }
        }
        if (ex instanceof BindException be) {
            if (be.getBindingResult() != null && be.getBindingResult().getFieldError() != null) {
                String m = be.getBindingResult().getFieldError().getDefaultMessage();
                if (StringUtils.hasText(m)) {
                    msg = m;
                }
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(400, msg));
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception ex) {
        // 404：接口路径不存在，或者静态资源不存在。
        // 统一返回 Not Found，避免前端拿到默认的 HTML 404 页面。
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(404, "Not Found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(Exception ex) {
        // 405：例如接口只支持 POST，但前端误发 GET。
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body(405, "Method Not Allowed"));
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class,
        JsonProcessingException.class,
        JsonMappingException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        // 400：常见于 JSON 解析失败、参数缺失、类型不匹配。
        // 这里统一用 Bad Request，不把底层异常细节暴露给前端（也避免泄露实现信息）。
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(400, "Bad Request"));
    }

    @ExceptionHandler({ClientAbortException.class, HttpMessageNotWritableException.class})
    public ResponseEntity<Void> handleClientAbort(Exception ex) {
        // 客户端中断连接：例如用户刷新/关闭页面导致请求被浏览器取消。
        // 这不是服务端错误，不需要记录 ERROR 日志，返回 204 即可。
        log.debug("Client aborted connection: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        // 兜底：所有未被上面捕获的异常。
        // 这里会记录 ERROR 日志方便排查，但返回给前端的 msg 使用统一文案，
        // 避免把堆栈/内部实现信息暴露给客户端。
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body(500, "INTERNAL_SERVER_ERROR"));
    }

    private Map<String, Object> body(int code, String msg) {
        // 统一响应体结构：{code, msg}
        // code 与 HTTP Status 一致，方便前端统一处理。
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("msg", msg);
        return map;
    }
}
