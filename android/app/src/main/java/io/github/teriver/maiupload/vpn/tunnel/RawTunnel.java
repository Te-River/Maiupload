package io.github.teriver.maiupload.vpn.tunnel;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class RawTunnel extends Tunnel {

    public RawTunnel(InetSocketAddress serverAddress, Selector selector) throws Exception {
        super(serverAddress, selector);
    }

    public RawTunnel(SocketChannel innerChannel, Selector selector) throws Exception {
        super(innerChannel, selector);
    }

    @Override
    protected void onConnected(ByteBuffer buffer) throws Exception {
        onTunnelEstablished();
    }

    @Override
    protected void beforeSend(ByteBuffer buffer) throws Exception {
        // 原样透传，无需改写
    }

    @Override
    protected void afterReceived(ByteBuffer buffer) throws Exception {
        // 原样透传，无需改写
    }

    @Override
    protected boolean isTunnelEstablished() {
        return true;
    }

    @Override
    protected void onDispose() {
        // 无需清理：原样透传隧道无自持资源
    }

}
