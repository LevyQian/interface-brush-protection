package cn.zeroeden.interfaceBrushProtection.controller;

import cn.zeroeden.interfaceBrushProtection.constant.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Zero
 * @time: 2023/2/14
 * @description:
 */

@RestController
@RequestMapping("/pass")
@Slf4j
public class PassController {

    @GetMapping("/get")
    public Result get(){
        log.info("执行【pass】-get()方法");
        return Result.SUCCESS();
    }
    @PostMapping("/post")
    public Result post(){
        log.info("执行【pass】-post()方法");
        return Result.SUCCESS();
    }

    @PutMapping("/put")
    public Result put(){
        log.info("执行【pass】-put()方法");
        return Result.SUCCESS();
    }

    @DeleteMapping("/delete")
    public Result delete(){
        log.info("执行【pass】-delete()方法");
        return Result.SUCCESS();
    }
}
