# HeliBoard (Soniox voice input)

Кастомная версия HeliBoard с интеграцией **Soniox** для голосового ввода (speech-to-text).

## Что изменено

Основная и единственная добавленная функция — **диктовка через Soniox Realtime STT**.

- Голосовой ввод работает через WebSocket API Soniox (модель по умолчанию: `stt-rt-preview`)
- На клавиатуре есть **отдельная кнопка микрофона** слева от Enter — не нужно искать микрофон в тулбаре
- При записи кнопка показывает состояние: синяя пульсация при подключении, зелёная — при прослушивании (реагирует на громкость голоса)
- Можно включить звук и вибрацию в момент, когда микрофон действительно готов принимать речь

## Настройка

**Настройки → Voice & privacy**

1. **Voice input provider** — выберите `Soniox API`
2. **Soniox API key** — ключ из [console.soniox.com](https://console.soniox.com)
3. **Voice key placement** — `Dedicated key (left of Enter)` для отдельной кнопки микрофона

## Сборка

```powershell
.\gradlew assembleDebugNoMinify
```

APK: `app/build/outputs/apk/debugNoMinify/HeliBoard_3.9-debugNoMinify.apk`

## Лицензия

Проект основан на HeliBoard (GPL-3.0). См. [LICENSE](/LICENSE).
