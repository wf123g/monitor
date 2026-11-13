package com.wf.monitor;

import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiRobotSendRequest;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.taobao.api.ApiException;
import com.wf.monitor.mapper.bugNotifyRecord;
import com.wf.monitor.vo.BugMessage;
import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;


public class customRobotGroupMessage {
private final bugNotifyRecord bugNotifyRecord;
   
    public static final String CUSTOM_ROBOT_TOKEN = System.getenv("DINGTALK_ROBOT_TOKEN");


    public static final String SECRET = System.getenv("DINGTALK_SIGNING_SECRET");
    private  final List<BugMessage> messages;

    public customRobotGroupMessage(List<BugMessage> message, bugNotifyRecord bugNotifyRecord) {
        this.messages = message;
        this.bugNotifyRecord = bugNotifyRecord;
    }



    public  void PostMessage() {
        if (CUSTOM_ROBOT_TOKEN == null || CUSTOM_ROBOT_TOKEN.isBlank()
                || SECRET == null || SECRET.isBlank()) {
            throw new IllegalStateException("请设置 DINGTALK_ROBOT_TOKEN 和 DINGTALK_SIGNING_SECRET 环境变量");
        }
        try {
            Long timestamp = System.currentTimeMillis();
            System.out.println(timestamp);
            String secret = SECRET;
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes("UTF-8"));
            String sign = URLEncoder.encode(new String(Base64.encodeBase64(signData)),"UTF-8");
            //sign字段和timestamp字段必须拼接到请求URL上，否则会出现 310000 的错误信息
            DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/robot/send?sign="+sign+"&timestamp="+timestamp);
            OapiRobotSendRequest req = new OapiRobotSendRequest();
            /**
             * 发送文本消息
             */

            for(BugMessage message : messages) {
                //定义文本内容
                OapiRobotSendRequest.Text text = new OapiRobotSendRequest.Text();
                StringBuilder  content = new StringBuilder();
                //定义 @ 对象
                OapiRobotSendRequest.At at = new OapiRobotSendRequest.At();
                content.append("第"+message.getExcelRowNumber()+"行,问题描述:"+message.getProblemDescription()+"\n");
                text.setContent("有新bug:"+ content);
                if(message.getMobile()!=null){
                    at.setAtMobiles(List.of(message.getMobile()));
                }
                if(message.getDingTalkUserId()!=null){
                    at.setAtUserIds(List.of(message.getDingTalkUserId()));
                }
                //设置消息类型
                req.setMsgtype("text");
                req.setText(text);
                req.setAt(at);
                OapiRobotSendResponse rsp = client.execute(req, CUSTOM_ROBOT_TOKEN);
                System.out.println(rsp.getBody());
                if (Long.valueOf(0L).equals(rsp.getErrcode())) {
                    bugNotifyRecord.updateStatus(message.getExcelRowNumber(), 2);
                }
            }


        } catch (ApiException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}
