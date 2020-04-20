/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.remoting.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import java.nio.ByteBuffer;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.common.RemotingUtil;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;

public class NettyDecoder extends LengthFieldBasedFrameDecoder {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(RemotingHelper.ROCKETMQ_REMOTING);

    private static final int FRAME_MAX_LENGTH =
        Integer.parseInt(System.getProperty("com.rocketmq.remoting.frameMaxLength", "16777216"));

    /**
     * 使用数据包长度的方式 进行数据包黏包 和 拆包
     * 定义 消息体的总长度 FRAME_MAX_LENGTH : com.rocketmq.remoting.frameMaxLength 进行自定义 默认值是 16M
     * lengthFieldOffset : 偏移量  比如 在消息的头部开头处 还有其他的meta信息,需要进行一定的偏移量才能到长度域的位置
     * lengthFieldLength : 长度域的长度 4
     * lengthAdjustment : 针对 长度量在最前面 而 头部的原信息在长度的后面 比如:
     * BEFORE DECODE (17 bytes)                      AFTER DECODE (17 bytes)
     * +----------+----------+----------------+      +----------+----------+----------------+
     * |  Length  | Header 1 | Actual Content |----->|  Length  | Header 1 | Actual Content |
     * | 0x00000C |  0xCAFE  | "HELLO, WORLD" |      | 0x00000C |  0xCAFE  | "HELLO, WORLD" |
     * +----------+----------+----------------+      +----------+----------+----------------+
     *
     * 如果 lengthAdjustment的长度为负数  表示 整个消息的长度为 x 需要在 总 x上减少 多少后 就是需要的消息体；
     *
     * initialBytesToStrip: 在拆完包之后 是否需要将长度域去除掉，需要去除掉，去除掉的长度是多少。这里是4 表示将4字节的长度域进行删除掉
     */
    public NettyDecoder() {
        super(FRAME_MAX_LENGTH, 0, 4, 0, 4);
    }

    /**
     * 收到一个完整包后 进行数据包的 解码
     */
    @Override
    public Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        ByteBuf frame = null;
        try {
            frame = (ByteBuf) super.decode(ctx, in);
            if (null == frame) {
                return null;
            }

            ByteBuffer byteBuffer = frame.nioBuffer();

            return RemotingCommand.decode(byteBuffer);
        } catch (Exception e) {
            log.error("decode exception, " + RemotingHelper.parseChannelRemoteAddr(ctx.channel()), e);
            RemotingUtil.closeChannel(ctx.channel());
        } finally {
            if (null != frame) {
                frame.release();
            }
        }

        return null;
    }
}
