package com.example.extension

/**
 * Kiwi/Chromium-style WebExtension compatibility surface for the WebView runtime.
 *
 * This does not copy Chromium's native C++ runtime; it mirrors the extension-facing
 * JavaScript API that Kiwi exposes so common MV2/MV3 extensions can run on Elephant.
 */
object KiwiExtensionApi {
    fun content(id: String, root: String): String = """
        (function(){
          window.chrome=window.chrome||{};
          var id=$id, root=$root, bridge=window.ElephantExtensionBridge;
          var event=function(){var f=[];return {addListener:function(fn){if(typeof fn==='function')f.push(fn)},removeListener:function(fn){f=f.filter(function(x){return x!==fn})},dispatch:function(){var a=arguments;f.slice().forEach(function(fn){try{fn.apply(null,a)}catch(e){}})}}};
          chrome.runtime=chrome.runtime||{};
          chrome.runtime.id=id;
          chrome.runtime.getURL=function(p){return root+p;};
          chrome.runtime.getManifest=function(){try{return JSON.parse(bridge.getManifest(id))}catch(e){return {}}};
          chrome.runtime.sendMessage=function(m,c){return new Promise(function(resolve){try{var r=bridge.sendMessage(id,JSON.stringify(m));var v=r?JSON.parse(r):undefined;if(c)c(v);resolve(v)}catch(e){if(c)c(undefined);resolve(undefined)}})};
          chrome.runtime.onMessage=chrome.runtime.onMessage||event();
          chrome.runtime.onInstalled=chrome.runtime.onInstalled||event();
          chrome.runtime.onStartup=chrome.runtime.onStartup||event();
          chrome.runtime.lastError=undefined;
          chrome.storage=chrome.storage||{};
          chrome.storage.local={
            get:function(k,c){return new Promise(function(resolve){try{var v=JSON.parse(bridge.storageGet(id,typeof k==='string'?k:null)||'{}');if(c)c(v);resolve(v)}catch(e){if(c)c({});resolve({})}})},
            set:function(v,c){return new Promise(function(resolve){try{bridge.storageSet(id,JSON.stringify(v||{}));if(c)c();resolve()}catch(e){if(c)c();resolve()}})},
            remove:function(k,c){return new Promise(function(resolve){try{bridge.storageRemove(id,k);if(c)c();resolve()}catch(e){if(c)c();resolve()}})},
            clear:function(c){return new Promise(function(resolve){try{bridge.storageClear(id);if(c)c();resolve()}catch(e){if(c)c();resolve()}})}
          };
          window.__elephantRuntimeOnMessage=function(m,s,r){chrome.runtime.onMessage.dispatch(m,s,r)};
          window.__elephantRuntimeOnInstalled=function(d){chrome.runtime.onInstalled.dispatch(d)};
          window.__elephantRuntimeOnStartup=function(){chrome.runtime.onStartup.dispatch()};
          chrome.tabs=chrome.tabs||{};
          chrome.tabs.query=function(q,c){return new Promise(function(resolve){try{var v=JSON.parse(bridge.tabsQuery(JSON.stringify(q||{}))||'[]');if(c)c(v);resolve(v)}catch(e){if(c)c([]);resolve([])}})};
          chrome.tabs.sendMessage=function(t,m,c){return new Promise(function(resolve){try{var v=JSON.parse(bridge.tabsSendMessage(id,t,JSON.stringify(m))||'null');if(c)c(v);resolve(v)}catch(e){if(c)c(undefined);resolve(undefined)}})};
          chrome.tabs.update=function(t,p,c){return new Promise(function(resolve){try{var v=JSON.parse(bridge.tabsUpdate(id,t,JSON.stringify(p||{}))||'null');if(c)c(v);resolve(v)}catch(e){if(c)c(undefined);resolve(undefined)}})};
          chrome.tabs.create=function(p,c){return new Promise(function(resolve){try{var v=JSON.parse(bridge.tabsCreate(id,JSON.stringify(p||{}))||'null');if(c)c(v);resolve(v)}catch(e){if(c)c(undefined);resolve(undefined)}})};
          chrome.tabs.remove=function(t,c){try{var r=bridge.tabsRemove(id,t);if(c)c(r?JSON.parse(r):undefined)}catch(e){if(c)c()}};
          chrome.tabs.onCreated=chrome.tabs.onCreated||event();
          chrome.tabs.onUpdated=chrome.tabs.onUpdated||event();
          chrome.tabs.onActivated=chrome.tabs.onActivated||event();
          chrome.tabs.onRemoved=chrome.tabs.onRemoved||event();
          window.__elephantTabsCreated=function(t){chrome.tabs.onCreated.dispatch(t)};
          window.__elephantTabsUpdated=function(t,c,i){chrome.tabs.onUpdated.dispatch(t,c,i)};
          window.__elephantTabsActivated=function(i){chrome.tabs.onActivated.dispatch({tabId:i})};
          chrome.scripting=chrome.scripting||{};
          chrome.scripting.executeScript=function(o,c){return new Promise(function(resolve){try{var v=JSON.parse(bridge.executeScript(id,JSON.stringify(o||{}))||'[]');if(c)c(v);resolve(v)}catch(e){if(c)c([]);resolve([])}})};
          chrome.windows=chrome.windows||{};
          chrome.windows.getCurrent=function(c){try{var r=bridge.windowsGetCurrent(id);if(c)c(r?JSON.parse(r):{})}catch(e){if(c)c({id:1,type:'normal'})}};
          chrome.windows.getLastFocused=chrome.windows.getCurrent;
          var action=chrome.action||chrome.browserAction||{};
          action.onClicked=action.onClicked||event();
          action.setBadgeText=function(d,c){try{bridge.actionSetBadgeText(id,typeof d==='string'?d:(d&&d.text)||'');if(c)c()}catch(e){if(c)c()}};
          action.getBadgeText=function(d,c){try{var r=bridge.actionGetBadgeText(id);if(c)c(r||'')}catch(e){if(c)c('')}};
          action.setBadgeBackgroundColor=function(d,c){try{bridge.actionSetBadgeBackgroundColor(id,JSON.stringify(d||{}));if(c)c()}catch(e){if(c)c()}};
          chrome.action=action;
          chrome.browserAction=action;
          chrome.notifications=chrome.notifications||{};
          chrome.notifications.create=function(i,o,c){try{var r=bridge.notificationsCreate(id,typeof i==='string'?i:'',JSON.stringify(o||{}));if(c)c(r)}catch(e){if(c)c('')}}; 
          chrome.notifications.clear=function(i,c){try{var r=bridge.notificationsClear(id,i);if(c)c(!!r)}catch(e){if(c)c(false)}};
          chrome.contextMenus=chrome.contextMenus||{};
          chrome.contextMenus.create=function(o,c){try{var r=bridge.contextMenuCreate(id,JSON.stringify(o||{}));if(c)c(r)}catch(e){if(c)c(-1)}};
          chrome.contextMenus.remove=function(i,c){try{bridge.contextMenuRemove(id,String(i));if(c)c()}catch(e){if(c)c()}};
          chrome.contextMenus.removeAll=function(c){try{bridge.contextMenuRemoveAll(id);if(c)c()}catch(e){if(c)c()}};
          chrome.webNavigation=chrome.webNavigation||{onCommitted:event(),onCompleted:event(),onHistoryStateUpdated:event()};
          chrome.commands=chrome.commands||{onCommand:event()};
        })();
    """.trimIndent()

    fun background(id: String, root: String): String = content(id, root) + """
        window.__elephantExtensionBackgroundReady=true;
        if(window.__elephantRuntimeOnStartup)window.__elephantRuntimeOnStartup();
        if(window.__elephantRuntimeOnInstalled)window.__elephantRuntimeOnInstalled({reason:'install'});
    """.trimIndent()
}
