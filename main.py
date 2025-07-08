
import os
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.utils import platform

# AndroidのWebViewを呼び出すためのpyjniusコード
if platform == 'android':
    from jnius import autoclass
    from android.runnable import run_on_ui_thread

    # Androidのクラスを取得
    WebView = autoclass('android.webkit.WebView')
    WebViewClient = autoclass('android.webkit.WebViewClient')
    Activity = autoclass('org.kivy.android.PythonActivity').mActivity

class GeminiBrowser(App):
    def build(self):
        layout = BoxLayout(orientation='vertical')
        if platform == 'android':
            self.webview = self.create_webview()
            layout.add_widget(self.webview)
        else:
            from kivy.uix.label import Label
            layout.add_widget(Label(text='WebView is only available on Android.'))
        return layout

    @run_on_ui_thread
    def create_webview(self):
        # WebViewのインスタンスを作成
        webview = WebView(Activity)
        
        # WebViewの設定
        settings = webview.getSettings()
        settings.setJavaScriptEnabled(True)
        settings.setDomStorageEnabled(True)
        
        # WebViewClientを設定して、アプリ内でURLを開くようにする
        webview.setWebViewClient(WebViewClient())
        
        # URLをロード
        webview.loadUrl('https://gemini.google.com')
        
        return webview

if __name__ == '__main__':
    GeminiBrowser().run()
