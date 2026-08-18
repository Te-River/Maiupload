package io.github.teriver.maiupload.core.proxy;

import android.util.Log;

import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;
import io.github.teriver.maiupload.GlobalViewModel;
import io.github.teriver.maiupload.core.prober.divingfish.DivingFishOAuthUtil;
import io.github.teriver.maiupload.core.utils.WechatRequestUtil;


public class HttpServer extends NanoHTTPD {
    public static int Port = 8284;
    private final static String TAG = "HttpServer";

    protected HttpServer() throws IOException {
        super(Port);
    }

    @Override
    public void start() throws IOException {
        super.start();
        Log.d(TAG, "Http server running on http://localhost:" + Port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        Log.d(TAG, "Serve request: " + session.getUri());
        if (session.getUri().equals("/divingfish/oauth/callback")) {
            // 水鱼 OAuth 本地回调：浏览器授权后跳回本地址，携带 code + state，
            // 交给 Kotlin 侧换 token（PKCE 无需 client_secret），返回结果页给浏览器。
            String code = session.getParms().get("code");
            String state = session.getParms().get("state");
            String html = DivingFishOAuthUtil.handleLocalCallback(code, state);
            return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html);
        } else if (session.getUri().equals("/auth/maimai")) {
            if (GlobalViewModel.INSTANCE.getMaimaiHooking()) {
                return onHooking();
            }
            return redirectToWechatAuthUrl(session, "maimai-dx");
        } else if (session.getUri().equals("/auth/chunithm")) {
            if (GlobalViewModel.INSTANCE.getChuniHooking()) {
                return onHooking();
            }
            return redirectToWechatAuthUrl(session, "chunithm");
        } else if(session.getUri().equals("/0")){
            return redirectToAuthUrlWithRandomParm(session, "maimai");
        } else if(session.getUri().equals("/1")) {
            return redirectToAuthUrlWithRandomParm(session, "chunithm");
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "");
    }

    private Response onHooking() {
        return newFixedLengthResponse(
                        Response.Status.ACCEPTED, MIME_HTML,
                        "<html><body><h1>查分进程已开始，请耐心等待</h1></body></html>"
                );
    }

    // To avoid fu***ing cache of wechat webview client
    private Response redirectToAuthUrlWithRandomParm(IHTTPSession session, String gameType) {
        Response r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "");
        r.addHeader("Location",
                "http://" + "127.0.0.1:8284" + "/auth/" + gameType + "?random=" + System.currentTimeMillis());
        return r;
    }

    private Response redirectToWechatAuthUrl(IHTTPSession session, String gameType) {
        String url = WechatRequestUtil.getAuthUrl(gameType);
        Log.d(TAG, url);

        Response r = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "");
        r.addHeader("Location", url);
        r.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        r.addHeader("Pragma", "no-cache");
        r.addHeader("Expires", "0");
        return r;
    }
}
