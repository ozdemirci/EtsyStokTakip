# Agent Talimatlari

- Prod amacli profil icin `application.properties` kullan.
- Commit mesajlari kisa ve Ingilizce olmali, fiil ile baslamalidir.
- Uygulama **schema-based** multi-tenancy mimarisi kullanir. Kodunuz bu yapinin gerektirdigi sekilde tenant bazli olmalidir.
- Tenant secimi HTTP istegindeki `X-TenantId` basligi uzerinden yapilir.
- Java 17 ve Maven kullanilir. Build islemlerinde `mvn` komutlarini tercih edin.
-mümkün oldukça lombok kullanmaya çalış
-tenants lowercase olarak tanımla ve çalıştır
-postgresql de stockify adında database olmalı.
-uygulama başlatmak için mvn spring-boot:run komutunu kullan










