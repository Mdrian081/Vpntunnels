# VPN App

Firebase থেকে server list নিয়ে VLESS · VMess · Trojan · SSH সব protocol সাপোর্ট করা Android VPN App।

## ✅ Setup করার ধাপ

### ১. Firebase সেটআপ

1. [Firebase Console](https://console.firebase.google.com) এ যান
2. আপনার existing project select করুন (যেটা admin panel ব্যবহার করে)
3. **Project Settings → General → Your apps → Add app → Android**
4. Package name দিন: `com.vpnapp`
5. `google-services.json` download করুন
6. এই ফাইলটা `android/app/google-services.json` তে replace করুন

### ২. GitHub এ Push করুন

```bash
git init
git add .
git commit -m "Initial VPN app"
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git push -u origin main
```

### ৩. APK Build করুন

- GitHub repository তে গিয়ে **Actions** tab এ click করুন
- **Build APK** workflow automatically চলবে
- Build শেষে **Artifacts** থেকে `vpn-app-debug.apk` download করুন

## 📱 App কীভাবে কাজ করে

1. App open করলে Firebase থেকে active server list আসে
2. Server এ tap করলে select হয়
3. **কানেক্ট** button চাপলে:
   - Android VPN permission চাইবে → Allow করুন
   - sing-box core দিয়ে tunnel তৈরি হবে
   - সমস্ত traffic VPN দিয়ে যাবে
4. **ডিসকানেক্ট** চাপলে tunnel বন্ধ হবে

## 🔧 Protocol Support

| Protocol | সাপোর্ট |
|----------|---------|
| VLESS | ✅ WebSocket / gRPC / TCP |
| VMess | ✅ WebSocket / gRPC / TCP |
| Trojan | ✅ WebSocket / TCP |
| SSH | ✅ Password auth |
| V2Ray | ✅ VMess/VLESS based |

## ⚠️ গুরুত্বপূর্ণ নোট

- `android/app/google-services.json` অবশ্যই real Firebase config দিয়ে replace করতে হবে
- Firebase Realtime Database rules এ read access দিতে হবে (বা authentication যোগ করতে হবে)
- APK build করতে GitHub Actions ব্যবহার করুন
