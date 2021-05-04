package burp;

import java.util.HashMap;

public class HTTP2Scan extends SmuggleScanBox implements IScannerCheck {

    HTTP2Scan(String name) {
        super(name);
    }

    public boolean doConfiguredScan(byte[] original, IHttpService service, HashMap<String, Boolean> config) {
        if (Utilities.globalSettings.getBoolean("skip vulnerable hosts") && BurpExtender.hostsToSkip.containsKey(service.getHost())) {
            return false;
        }

        original = setupRequest(original);
        original = Utilities.addOrReplaceHeader(original, "User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36");
        original = Utilities.addOrReplaceHeader(original, "Transfer-Encoding", "chunked");
        original = Utilities.addOrReplaceHeader(original, "X-Come-Out-And-Play", "1");
        original = Utilities.addOrReplaceHeader(original, "Connection", "close");

        String attackCode = String.join("|", config.keySet());
        byte[] syncedReq = makeChunked(original, 0, 0, config, false);
        Resp syncedResp = request(service, syncedReq);
        if (!syncedResp.failed()) {
            if (!Utilities.containsBytes(syncedResp.getReq().getResponse(), "HTTP/2 ".getBytes())) {
                BurpExtender.hostsToSkip.put(service.getHost(), true);
                return false;
            }

            // todo send a followup without TE to make sure chunked encoding is relevant, reducing FPs
            byte[] attackReq = makeChunked(original, 0, 10, config, false);
            Resp attack = request(service, attackReq);
            if (attack.timedOut() && !request(service, syncedReq).timedOut() && !request(service, syncedReq).timedOut() && request(service, attackReq).timedOut()) {

                byte[] brokenAttackReq = Utilities.replace(attackReq, "ransfer-Encoding", "zansfer-Zncoding");
                Resp brokenAttack = request(service, brokenAttackReq);
                if (!brokenAttack.timedOut()) {
                    attackCode += " tested";
                }
                attack = request(service, attackReq);
                if (!attack.timedOut()) {
                    return true;
                }

                report("HTTP/2 TE desync v10a "+attackCode, ".", syncedResp, brokenAttack, attack);
                ChunkContentScan.sendPoc(original, service, true, config);
                return true;
            } else if (attack.failed() && !request(service, syncedReq).failed() && !request(service, syncedReq).failed() && request(service, attackReq).failed()) {
                byte[] brokenAttackReq = Utilities.replace(attackReq, "ransfer-Encoding", "zansfer-Zncoding");
                Resp brokenAttack = request(service, brokenAttackReq);

                if (!brokenAttack.failed()) {
                    attackCode += " tested";
                }
                attack = request(service, attackReq);
                if (!attack.failed()) {
                    return true;
                }

                report("HTTP/2 TE desync v10b "+attackCode, ".", syncedResp, brokenAttack, attack);
                ChunkContentScan.sendPoc(original, service, true, config);
                return true;
            }
        }

        // dodgy but worthwhile as HEAD-detection is a bit unreliable
        //syncedReq = makeChunked(original, -1, 0, config, false);


//        syncedReq = Utilities.setBody(original, "abcd=def");
//        syncedReq = Utilities.replace(syncedReq, "Transfer-Encoding", "nope");
//        syncedReq = Utilities.setHeader(syncedReq, "Content-Length", "6");
//
//        Resp syncedResp = request(service, syncedReq);
//
//        if (Utilities.contains(syncedResp, "cloudfront")) {
//            return false;
//        }
//
////        if (syncedResp.getStatus() > 399 ) {
////            return false;
////        }
//
//        if (!Utilities.containsBytes(syncedResp.getReq().getResponse(), "HTTP/2 ".getBytes())) {
//            BurpExtender.hostsToSkip.put(service.getHost(), true);
//            return false;
//        }
//
//        // if they reject this they probably just don't like a content-type mismatch
//        if (!syncedResp.failed()) {
//            byte[] attackReq = Utilities.setHeader(syncedReq, "Content-Length", "16");
//            //attackReq = Utilities.replace(attackReq, "Transfer-Encoding", "nope");
//            Resp attack = request(service, attackReq);
//            if (attack.timedOut() && !request(service, syncedReq).timedOut() && !request(service, syncedReq).timedOut() && request(service, attackReq).timedOut()) {
//                report("HTTP/2 CL desync v8a "+attackCode + " |"+syncedResp.getStatus(), ".", syncedResp, attack);
//                return true;
//            } else if (attack.failed() && !request(service, syncedReq).failed() && !request(service, syncedReq).failed() && request(service, attackReq).failed()) {
//                report("HTTP/2 CL desync v8b "+attackCode+ " |"+syncedResp.getStatus(), ".", syncedResp, attack);
//                return true;
//            }
//        }

       return false;
    }
}