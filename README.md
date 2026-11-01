# Лабораторная работа №2 — Веб-программирование

Перфилов Алексей Михайлович, P3209. Вариант 824152.

Продолжение [первой лабораторной](https://github.com/AlexPer777/lab1_web). Проверка попадания точки перенесена на Java FastCGI. Страница отправляет GET-запрос через `fetch` и выводит результат без перезагрузки. Apache на Helios отдаёт HTML и JS, а запросы к `/fcgi-bin/server.jar` передаёт Java.

В таблице сохраняются координаты, R, результат, время сервера и время обработки в миллисекундах. История хранится в `localStorage`. На графике показываются точки с выбранным R.

## Вариант

| Параметр | Значения | Ввод |
| --- | --- | --- |
| X | −3, −2, −1, 0, 1, 2, 3, 4, 5 | Радиокнопки |
| Y | `−3 < Y < 3` | Текстовое поле |
| R | 1, 1.5, 2, 2.5, 3 | Выпадающий список |

Область состоит из трёх фигур, включая их границы:

| Фигура | Условия |
| --- | --- |
| Прямоугольник во II четверти | `−R/2 ≤ x ≤ 0`, `0 ≤ y ≤ R` |
| Четверть круга в III четверти | `x ≤ 0`, `y ≤ 0`, `x² + y² ≤ R²/4` |
| Треугольник в IV четверти | `x ≥ 0`, `y ≤ 0`, `y ≥ 2x − R` |

Данные проверяются и в браузере, и на сервере. Расчёты в Java выполняются через `BigDecimal`.

## Файлы

- `index.html` — страница и CSS.
- `index.js` — форма, запросы, график и история.
- `server/src/Main.java` — приём FastCGI-запросов и JSON-ответы.
- `server/src/PointRequest.java` — разбор и проверка параметров.
- `server/src/AreaChecker.java` — проверка попадания.
- `server/test/ServerTest.java` — тесты параметров и границ области.
- `server/manifest.mf` — главный класс и путь к библиотеке.
- `server/lib/fastcgi-lib.jar` — библиотека из задания.
- `apache/httpd.conf` — конфигурация Apache для Helios.

## Сборка

Нужен JDK 21 или новее. Локально используется JDK 27, но сборка рассчитана на Java 21 на Helios. Команды выполняются из корня проекта:

```sh
mkdir -p server/build/classes
javac --release 21 -encoding UTF-8 -cp server/lib/fastcgi-lib.jar -d server/build/classes server/src/*.java
jar --create --file server/build/server.jar --manifest server/manifest.mf -C server/build/classes .
```

## Запуск на Helios

В `apache/httpd.conf` указаны каталог `/home/studs/s501851/httpd-root`, порт Apache `24666` и порт FastCGI `24777`. Для другого пользователя нужно изменить эти значения.

Перенос файлов с Mac:

```sh
ssh -p 2222 s501851@helios.cs.ifmo.ru 'mkdir -p ~/httpd-root/conf ~/httpd-root/public ~/httpd-root/fcgi-bin ~/httpd-root/run'
scp -P 2222 apache/httpd.conf s501851@helios.cs.ifmo.ru:~/httpd-root/conf/
scp -P 2222 index.html index.js s501851@helios.cs.ifmo.ru:~/httpd-root/public/
scp -P 2222 server/build/server.jar server/lib/fastcgi-lib.jar s501851@helios.cs.ifmo.ru:~/httpd-root/fcgi-bin/
```

На Helios проверить конфигурацию и после `Syntax OK` запустить Apache:

```sh
chmod 700 ~/httpd-root/run
httpd -t -f ~/httpd-root/conf/httpd.conf
httpd -f ~/httpd-root/conf/httpd.conf -k start
```

В отдельном SSH-терминале на Helios запустить Java:

```sh
java -DFCGI_PORT=24777 -jar ~/httpd-root/fcgi-bin/server.jar
```

`fastcgi-lib.jar` должен лежать рядом с `server.jar`. Терминал с Java нужно оставить открытым. Остановка — `Ctrl+C`.

На Mac открыть SSH-туннель и оставить соединение открытым:

```sh
ssh -p 2222 -L 8080:127.0.0.1:24666 s501851@helios.cs.ifmo.ru
```

Страница доступна по адресу [localhost:8080](http://localhost:8080/). Для подключения к Helios нужен VPN. При открытии `index.html` как файла запросы к серверу работать не будут.

## Обновление

- После изменения HTML или JS скопировать файлы в `~/httpd-root/public/` и обновить страницу.
- После изменения Java пересобрать JAR, остановить Java на Helios, скопировать новый JAR и запустить Java снова.
- После изменения конфигурации скопировать её на Helios, выполнить `httpd -t` и применить через `httpd -f ~/httpd-root/conf/httpd.conf -k restart`.

Остановка Apache:

```sh
httpd -f ~/httpd-root/conf/httpd.conf -k stop
```

Ошибки Apache записываются в `~/httpd-root/error.log`, обращения — в `~/httpd-root/access.log`. Если в error.log появляется `Connection refused`, проверить, запущена ли Java на порту `24777`.

## Проверка

После сборки сервера:

```sh
mkdir -p server/build/test-classes
javac --release 21 -encoding UTF-8 -cp server/build/classes -d server/build/test-classes server/test/ServerTest.java
java -cp server/build/classes:server/build/test-classes ServerTest
```

Ожидаемый вывод — `Passed: 53`. Тесты запускаются без Apache.

При работающем туннеле можно проверить сервер из терминала на Mac:

```sh
curl -i 'http://localhost:8080/fcgi-bin/server.jar?x=1&y=-0.5&r=2.5'
curl -i 'http://localhost:8080/fcgi-bin/server.jar?x=1&y=3&r=2.5'
curl -i -X POST 'http://localhost:8080/fcgi-bin/server.jar'
```

Первый запрос возвращает `200 OK` и `hit: true`, второй — `400 Bad Request`, третий — `405 Method Not Allowed`. Пример успешного ответа:

```json
{"x":1,"y":-0.5,"r":2.5,"hit":true,"timestamp":"2026-11-01T12:00:00Z","executionTimeMs":0.25}
```

`timestamp` — время сервера в UTC, `executionTimeMs` — время проверки и расчёта в миллисекундах, без передачи ответа по сети.

В браузере стоит проверить границы фигур, сохранение истории после перезагрузки и её очистку. История у `localhost:8080` и другого адреса или порта будет разной.

## Вопросы к защите

1. Синхронная и асинхронная обработка HTTP-запросов. AJAX.
2. jQuery: назначение, основные API, работа с DOM и AJAX.
3. Реализация AJAX с помощью SuperAgent.
4. CGI: определение, назначение, ключевые особенности.
5. FastCGI: преимущества и недостатки относительно CGI.
6. FastCGI-сервер на Java.
