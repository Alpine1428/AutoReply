package com.holyworld.autoreply.ai;

import com.holyworld.autoreply.HolyWorldAutoReply;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ResponseEngine {

    private final ConcurrentHashMap<String, PlayerState> playerStates = new ConcurrentHashMap<>();
    private final List<ResponseRule> rules = new ArrayList<>();

    public ResponseEngine() {
        initializeRules();
        HolyWorldAutoReply.LOGGER.info("[Engine] Loaded {} rules", rules.size());
    }

    // === Player State ===
    public static class PlayerState {
        public long checkStartTime;
        public int messageCount = 0;
        public boolean askedForAnydesk = false;
        public boolean gaveCodes = false;
        public boolean offeredConfession = false;
        public boolean mentionedRustdesk = false;
        public boolean toldHowToDownload = false;
        public boolean warnedAboutTime = false;
        public boolean warnedRefusal = false;
        public boolean warnedConfession = false;
        public String lastCategory = "";

        public PlayerState() {
            this.checkStartTime = System.currentTimeMillis();
        }

        public int getRemainingMinutes() {
            int elapsed = (int)((System.currentTimeMillis() - checkStartTime) / 60000);
            return Math.max(1, 7 - elapsed);
        }

        public long getElapsedMinutes() {
            return (System.currentTimeMillis() - checkStartTime) / 60000;
        }
    }

    // === Result with action type ===
    public static class EngineResult {
        public enum Action { REPLY, BAN_INSULT, BAN_REFUSAL, BAN_CONFESSION }
        public final Action action;
        public final String message;
        public final String banReason;
        public final String banDuration;

        private EngineResult(Action action, String message, String banDuration, String banReason) {
            this.action = action;
            this.message = message;
            this.banDuration = banDuration;
            this.banReason = banReason;
        }

        public static EngineResult reply(String msg) {
            return new EngineResult(Action.REPLY, msg, null, null);
        }

        public static EngineResult banInsult(String nick) {
            return new EngineResult(Action.BAN_INSULT, null, "30d", "Insult");
        }

        public static EngineResult banRefusal(String nick) {
            return new EngineResult(Action.BAN_REFUSAL, null, "30d", "Refusal");
        }

        public static EngineResult banConfession(String nick) {
            return new EngineResult(Action.BAN_CONFESSION, null, "20d", "Confession");
        }
    }

    @FunctionalInterface
    private interface Matcher {
        boolean test(String msg, String low, PlayerState st, String name);
    }

    @FunctionalInterface
    private interface Responder {
        EngineResult get(String msg, String low, PlayerState st, String name);
    }

    private static class ResponseRule {
        final String cat;
        final int pri;
        final Matcher matcher;
        final Responder responder;

        ResponseRule(String cat, int pri, Matcher m, Responder r) {
            this.cat = cat; this.pri = pri; this.matcher = m; this.responder = r;
        }
    }

    private static String pick(String... o) {
        return o[ThreadLocalRandom.current().nextInt(o.length)];
    }

    private static boolean has(String t, String... kw) {
        for (String k : kw) if (t.contains(k)) return true;
        return false;
    }

    private static boolean eq(String t, String... vals) {
        for (String v : vals) if (t.equals(v)) return true;
        return false;
    }

    private void initializeRules() {

        // ===================== 110: DIRECT REFUSAL WORD =====================
        rules.add(new ResponseRule("direct_refusal", 110,
            (m,l,s,n) -> {
                String t = l.trim();
                return eq(t, "отказ", "отказываюсь");
            },
            (m,l,s,n) -> EngineResult.banRefusal(n)));

        // ===================== 108: DIRECT CONFESSION WORD =====================
        rules.add(new ResponseRule("direct_confession", 108,
            (m,l,s,n) -> {
                String t = l.trim();
                return eq(t, "я чит", "я читер", "признание");
            },
            (m,l,s,n) -> EngineResult.banConfession(n)));

        // ===================== 100: INSULTS =====================
        rules.add(new ResponseRule("insult", 100,
            (m,l,s,n) -> has(l,
                "нахуй","нахуи","пошел нах","пошёл нах","иди нах",
                "хуй","хуи","хуе","хуё","хуесос","хуёсос",
                "ебал","ебан","ебат","ебу","ёба",
                "сука","суки","сучка","блядь","бляд",
                "далбаеб","долбаеб","долбоеб","дебил",
                "мразь","урод","гандон","гондон",
                "пидор","пидр","педик",
                "чмо","чмошник",
                "безмамн","мертвой мам","мёртвой мам",
                "твою мать","маму ебал","маме пизд",
                "пузо вырезал","сын бляд","сын свинь",
                "соси","саси","пизд","шлюха","тварь",
                "выродок","ублюд","конч","даун","кретин",
                "идиот","мусор","пёс","псина","шакал",
                "крыса","животное","скотина"),
            (m,l,s,n) -> EngineResult.banInsult(n)));

        // ===================== 97: THREATS =====================
        rules.add(new ResponseRule("threats", 97,
            (m,l,s,n) -> has(l,
                "я тебя найду","найду тебя","узнаю где живешь",
                "узнаю где живёшь","тебе конец","ты труп",
                "убью","зарежу","ударю","побью","накажу",
                "отомщу","я отомщу","мой брат","мой папа",
                "мой батя","знакомый хакер","взломаю",
                "дудосить","ддос","ddos","твой ip",
                "твой айпи","деаноним","сват","свачу"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Угрозы не помогут. Скачивай AnyDesk",
                "Мне не страшно) Жду аник",
                "Давай без угроз. Качай AnyDesk и пройди проверку"
            ))));

        // ===================== 95: CONFESSION (extended) =====================
        rules.add(new ResponseRule("confession", 95,
            (m,l,s,n) -> has(l,
                "я софт","я читер","я чит ","я читор",
                "я с софт","я с читами","я играю с чит",
                "у меня софт","у меня чит","у меня читы",
                "у меня x-ray","у меня xray",
                "я с модом","я с софтом",
                "я читер бб","я чит бань","я чит баньте",
                "у меня селестиал","у меня celestial",
                "с софтом бань","хочеш бань","хочешь бань",
                "признание ентити","за хранения забань",
                "у меня всего x-ray",
                "все равно айпи сменю","всё равно на этот акк",
                "бань нахуй","ладно я с читами",
                "да у меня читы","да я читер","я юзаю",
                "у меня есть софт","да я софтер",
                "ок я читер","окей я читер",
                "хорошо я признаюсь","ладно я софт",
                "го признание","готов признаться",
                "хочу признаться","могу признаться",
                "признаюсь что","я признаюсь"),
            (m,l,s,n) -> EngineResult.banConfession(n)));

        // ===================== 94: LEAVE =====================
        rules.add(new ResponseRule("leave", 94,
            (m,l,s,n) -> {
                String t = l.trim();
                return eq(t,"бб","bb","бай","пока","выхожу") ||
                    has(l,"bb all","бб всем","all bb",
                        "лад баньте","ладно баньте","ладна банте",
                        "давай бан","я жду бан","я ухожу",
                        "качать не охота","качать не буду",
                        "не буду ничего скачивать","бб короче",
                        "я выхожу","я выйду","пока всем",
                        "я ливаю","я ливну");
            },
            (m,l,s,n) -> {
                if (has(l,"удачи","пока","бай"))
                    return EngineResult.reply("Удачи! Но выход = отказ от проверки");
                return EngineResult.reply("Выход во время проверки = отказ. Лучше пройди проверку!");
            }));

        // ===================== 93: REFUSAL =====================
        rules.add(new ResponseRule("refusal", 93,
            (m,l,s,n) -> has(l,"мне лень","забань на минимальн",
                "эту залупу","баньте","бань ",
                "не буду скачивать","не буду качать","я отказываюсь от",
                "не хочу скачивать","не собираюсь",
                "не стану качать","забань просто","я отказ",
                "не буду проходить","не хочу проходить",
                "мне все равно","мне всё равно","мне пофиг",
                "забань и всё","забей","похуй на бан",
                "плевать на бан","мне похер"),
            (m,l,s,n) -> {
                if (!s.warnedRefusal) {
                    s.warnedRefusal = true;
                    return EngineResult.reply(
                        "Если вы отказываетесь от проверки или скачивания нужной программы напишите \"Отказ\" для бана"
                    );
                }
                return EngineResult.reply(pick(
                    "Отказ от проверки - 30 дней бана. Напиши \"Отказ\" если уверен",
                    "Лучше пройди проверку. Признание 20 дней, отказ 30 дней. Напиши \"Отказ\" для бана"
                ));
            }));

        // ===================== 92: CONFESSION QUESTION =====================
        rules.add(new ResponseRule("confess_short", 92,
            (m,l,s,n) -> {
                String t = l.trim();
                return eq(t,"признаюсь","признаю","призание") ||
                    has(l,"давай признание");
            },
            (m,l,s,n) -> {
                if (!s.warnedConfession) {
                    s.warnedConfession = true;
                    return EngineResult.reply(
                        "Для признания напиши \"Я чит\" для уменьшения срока"
                    );
                }
                return EngineResult.reply("Напиши \"Я чит\" для признания");
            }));

        // ===================== 88: CODE =====================
        rules.add(new ResponseRule("code", 88,
            (m,l,s,n) -> {
                String cleaned = m.replaceAll("[\\s\\-]", "");
                return cleaned.matches("\\d{6,10}");
            },
            (m,l,s,n) -> {
                s.gaveCodes = true;
                return EngineResult.reply(pick(
                    "Принимай запрос подключения",
                    "Подключаюсь, прими запрос в AnyDesk",
                    "Код принят! Жми Принять в AnyDesk",
                    "Сейчас подключусь. Нажми Принять"
                ));
            }));

        // ===================== 85: SENT IN PM =====================
        rules.add(new ResponseRule("sent_pm", 85,
            (m,l,s,n) -> has(l,
                "в лс скинул","скинул в лс","написал в лс",
                "кинул в лс","отправил в лс","лс глянь",
                "в личку скинул","проверь лс","чекни лс",
                "гляди лс","в лс написал","в лс кинул"),
            (m,l,s,n) -> {
                s.gaveCodes = true;
                return EngineResult.reply(pick(
                    "Принял! Прими запрос подключения",
                    "Вижу, подключаюсь",
                    "Сейчас зайду. Жми Принять"
                ));
            }));

        // ===================== 83: DISCORD =====================
        rules.add(new ResponseRule("discord", 83,
            (m,l,s,n) -> has(l,
                "через дс","давай дс","дс можно","го дс","го в дс",
                "го через дс","можно дс","мб дс","по дс",
                "давай в дс","го по дс","давай по дс",
                "через дискорд","можно дискорд","го дискорд",
                "мой дс","могу дс","могу в дс",
                "в звонок","пойдем в звонок",
                "го по диск","давай в дискорд",
                "скайп","тимспик","teamspeak","зум","zoom"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Проверка только через AnyDesk. Скачивай",
                "По дискорду проверки не проводим. Качай AnyDesk",
                "Нет. Только AnyDesk - anydesk com"
            ))));

        // ===================== 82: VK / TG =====================
        rules.add(new ResponseRule("vk_tg", 82,
            (m,l,s,n) -> has(l,"через вк","го вк","можно вк",
                "через тг","го тг","тг можно","можно тг",
                "есть тг","есть вк","го по вк","демонстрация",
                "стрим","трансляция","могу показать экран"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Только через AnyDesk. Качай",
                "Проверка только через AnyDesk. Скачивай"
            ))));

        // ===================== 81: TO PM =====================
        rules.add(new ResponseRule("lm", 81,
            (m,l,s,n) -> has(l,"можно в лс","могу в лс","кому в лс",
                "куда кидать","кому скинуть","куда скинуть",
                "кому код","куда код"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Мне в личные сообщения!",
                "Скинь мне в лс. Код из AnyDesk"
            ))));

        // ===================== 80: GREETING =====================
        rules.add(new ResponseRule("greeting", 80,
            (m,l,s,n) -> {
                if (s.messageCount > 3) return false;
                String t = l.trim();
                return has(t,"привет","прив","хай","здравств","приветик","прывект",
                    "добрый день","добрый вечер","доброе утро","салам","здаров",
                    "здарова","приветствую","дратути","дратуте","хелло","hello") ||
                    eq(t,"ку","qq","hi","yo","хеллоу");
            },
            (m,l,s,n) -> {
                if (has(l,"привет я не читер","я не читер привет"))
                    return EngineResult.reply("Привет! Тогда скачивай AnyDesk и докажи это");
                s.askedForAnydesk = true;
                return EngineResult.reply(pick(
                    "Это проверка на читы! У тебя 7 минут чтобы скачать AnyDesk и пройти проверку! Признание уменьшает наказание на 35%! Отказ/выход/игнор = Бан!",
                    "Привет! Жду AnyDesk - anydesk com, 7 мин",
                    "qq жду аник - anydesk com, 7 минут"
                ));
            }));

        // ===================== 78: REASON =====================
        rules.add(new ResponseRule("reason", 78,
            (m,l,s,n) -> has(l,
                "за что","причина","за что прове",
                "почему вызвал","за что вызвал",
                "почему меня","что я сделал","что я зделал",
                "что случилось","а за что",
                "какая проверка",
                "я тока зашёл","я только зашел",
                "в чем причина","зачем вызвал","за что проверка",
                "что произошло","почему проверка",
                "я ничего не делал","я ничего не зделал",
                "я не нарушал","за что меня"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Многочисленные репорты на тебя",
                "Модератор не обязан разглашать причину проверки",
                "На тебя поступили жалобы. Качай AnyDesk",
                "Репорты. Давай не будем тратить время - anydesk com"
            ))));

        // ===================== 77: NOT CHEATER =====================
        rules.add(new ResponseRule("not_cheater", 77,
            (m,l,s,n) -> has(l,
                "я не читер","я не читар","я не софт",
                "я чист","у меня нет читов","у меня нету читов",
                "без читов","без софта","я ансофт",
                "я 100%","я не использую","я легит",
                "я готов пройти","я не юзаю","я легитный",
                "у меня нет ничего","я чистый","клянусь",
                "клянус","честное слово","зуб даю",
                "мамой клянусь","богом клянусь"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Отлично! Скачивай AnyDesk и докажи!",
                "Верю! Тогда качай AnyDesk и быстро пройдешь",
                "Раз чист - скачивай AnyDesk. Проверимся за 2 минуты"
            ))));

        // ===================== 76: WHAT CHECK =====================
        rules.add(new ResponseRule("what_check", 76,
            (m,l,s,n) -> has(l,
                "что будешь смотреть","что ты будешь смотреть",
                "что проверяешь","что ты проверяешь",
                "что будешь проверять","как проверяешь",
                "что именно","а что смотришь","куда смотришь",
                "что ты смотришь","что надо проверить",
                "что ты ищешь","что ищешь"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Рабочий стол, процессы, папки на наличие читов",
                "Проверю ПК на наличие запрещенного ПО. Стандартная процедура",
                "Посмотрю есть ли у тебя софт. Это быстро"
            ))));

        // ===================== 75: WHAT IS ANYDESK =====================
        rules.add(new ResponseRule("what_anydesk", 75,
            (m,l,s,n) -> has(l,
                "что за аник","что такое аник","что за анидеск",
                "что такое анидеск","что это за прог",
                "что за прога","че за прога",
                "типо ты в моем","будешь лазать","управлять моим",
                "анидеск это что","что это за софт",
                "зачем мне это","а зачем скачивать",
                "это безопасно","это вирус","он опасн",
                "она опасн","анидеск безопасн"),
            (m,l,s,n) -> {
                if (has(l,"типо ты в моем","будешь лазать","управлять моим"))
                    return EngineResult.reply("Да, я посмотрю рабочий стол на наличие читов");
                if (has(l,"вирус","опасн","безопасн"))
                    return EngineResult.reply("AnyDesk - безопасная программа. Официальный сайт anydesk com");
                return EngineResult.reply(pick(
                    "Программа удаленного доступа. Через нее я проверю ПК на читы",
                    "Это программа для удаленного доступа - безопасная и официальная. anydesk com"
                ));
            }));

        // ===================== 74: DOWNLOADING =====================
        rules.add(new ResponseRule("downloading", 74,
            (m,l,s,n) -> has(l,
                "скачиваю","скачиваеться","скачивается",
                "качаю","качается","загружается","грузит",
                "устанавливаю","устанавливается",
                "пачти скачался","почти скачал",
                "немного осталось","щас скачаю","ща скачаю",
                "загрузил","скачал","жди качаю",
                "ок скачаю","я качаю","уже качаю",
                "загружаю","установил","скачался",
                "загрузился","готово","всё скачал"),
            (m,l,s,n) -> {
                if (has(l,"скачал","загрузил","скачался","установил","загрузился","готово","всё скачал"))
                    return EngineResult.reply(pick(
                        "Отлично! Кидай длинный код (Рабочее место / Ваш ID) мне в лс",
                        "Супер! Открывай и скинь цифры мне в личные сообщения",
                        "Запускай! Скинь код (длинное число) мне в лс"
                    ));
                return EngineResult.reply(pick(
                    "Жду, " + s.getRemainingMinutes() + " мин осталось",
                    "Ок, жду. Осталось " + s.getRemainingMinutes() + " мин",
                    "Хорошо, не затягивай"
                ));
            }));

        // ===================== 73: CANT DOWNLOAD -> RUSTDESK =====================
        rules.add(new ResponseRule("cant_dl", 73,
            (m,l,s,n) -> has(l,
                "не скачивается","не качается","не загружается",
                "не грузит","не могу скачать",
                "не работает","не робит","ошибка",
                "вирус","трояны","не дает скачать",
                "не запускается","немагу","не магу",
                "сайт не грузит","не открывается",
                "антивирус","бяка",
                "у меня не работает","у меня ошибка",
                "не могу установить","не устанавливается",
                "не получается","не выходит","не могу открыть",
                "заблокирован","заблокировано","блокирует",
                "не загружает","не грузится","зависло",
                "зависает","виснет","фризит",
                "не хочет скачиваться","невозможно",
                "не поддерживается","ошибка скачивания"),
            (m,l,s,n) -> {
                if (!s.mentionedRustdesk) {
                    s.mentionedRustdesk = true;
                    return EngineResult.reply("Скачивай RustDesk - аналог AnyDesk, работает везде! В браузере пиши rustdesk com");
                }
                return EngineResult.reply(pick(
                    "Попробуй другой браузер. Качай RustDesk с rustdesk com",
                    "Отключи антивирус и скачай RustDesk - rustdesk com",
                    "Все должно работать. Время идет!"
                ));
            }));

        // ===================== 72: NO ANYDESK =====================
        rules.add(new ResponseRule("no_anik", 72,
            (m,l,s,n) -> has(l,
                "нету аник","нет аник","у меня нету ани",
                "аника нет","анидеска нет",
                "нету такого","нету его",
                "у меня нету","нету программы",
                "просто нету","тут анидеска нет",
                "у меня нет аник","нет анидеск",
                "не установлен","нету на пк"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Скачивай! anydesk com",
                "Качай с сайта anydesk com, " + s.getRemainingMinutes() + " мин осталось",
                "В браузере пиши anydesk com - скачивай и открывай"
            ))));

        // ===================== 71: RUSTDESK =====================
        rules.add(new ResponseRule("rustdesk", 71,
            (m,l,s,n) -> has(l,
                "растдеск","растдекс","раст деск","rustdesk","rust desk",
                "рустдеск","руст деск","рудеск","rudesk",
                "можно по рудеск","рудеск сойдет","рустдеск можно",
                "подойдет рустдеск","подойдет рудеск","можно рустдеск"),
            (m,l,s,n) -> {
                s.mentionedRustdesk = true;
                if (has(l,"можно","подойдет","сойдет"))
                    return EngineResult.reply("Да, скачивай! rustdesk com");
                return EngineResult.reply(pick(
                    "Скачивай - rustdesk com",
                    "Да, качай!"
                ));
            }));

        // ===================== 69: WHERE / HOW DOWNLOAD =====================
        rules.add(new ResponseRule("where_dl", 69,
            (m,l,s,n) -> has(l,
                "где скачать","как скачать","хз как скачать",
                "с какого сайта","какая ссылка",
                "что скачать","что качать",
                "а что надо скачать","название ани",
                "а где код","где код найти","скинь ссылку",
                "ссылку дай","откуда качать","откуда скачать",
                "как установить","где установить","как найти",
                "где найти","подскажи как","помоги скачать",
                "не знаю как","не знаю где","хз где"),
            (m,l,s,n) -> {
                s.toldHowToDownload = true;
                if (has(l,"код","где код"))
                    return EngineResult.reply("При запуске сразу видно - Рабочее место или Ваш ID. Эти цифры скинь мне в лс");
                if (has(l,"название"))
                    return EngineResult.reply("AnyDesk - так и называется. anydesk com");
                return EngineResult.reply("В браузере пиши anydesk com - скачивай, запускай и скидывай цифры (Рабочее место или Ваш ID) мне в личные сообщения, не в общий чат!");
            }));

        // ===================== 68: PHONE =====================
        rules.add(new ResponseRule("phone", 68,
            (m,l,s,n) -> has(l,"я с телефон","с телефона","на телефоне",
                "с мобильн","на андроид","с планшета","на планшете",
                "с айфона","на айфоне","на ios","с ios",
                "с мобилы","мобильный","на мобиле"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "AnyDesk есть на телефон! Качай из Play Market / App Store",
                "Скачивай AnyDesk на телефон. Есть в магазине приложений"
            ))));

        // ===================== 67: WHAT NEXT =====================
        rules.add(new ResponseRule("what_next", 67,
            (m,l,s,n) -> has(l,
                "что дальше","чё дальше","что делать",
                "чё делать","что мне делать","чо делать",
                "что скидывать","что нужно делать",
                "как мне пройти","куда жмать",
                "я не понимаю","что мне надо делать",
                "и что теперь","ну и что","а дальше",
                "дальше что","куда нажать","что нажать",
                "куда тыкать","что тыкать"),
            (m,l,s,n) -> {
                if (s.gaveCodes)
                    return EngineResult.reply("Нажми зеленую кнопку Принять в AnyDesk");
                if (s.askedForAnydesk)
                    return EngineResult.reply("Кидай длинный код (Рабочее место / Ваш ID) мне в лс");
                return EngineResult.reply("В браузере пиши anydesk com - скачивай, запускай и скидывай цифры мне в личные сообщения!");
            }));

        // ===================== 66: TIME =====================
        rules.add(new ResponseRule("time", 66,
            (m,l,s,n) -> has(l,
                "скок времени","сколько времени","скок время",
                "скок минут","сколько минут","скок у меня",
                "сколько у меня","сколько ещё","сколько еще",
                "сколько осталось","скок осталось",
                "доп время","продли время","дай время",
                "можно доп","можно подождать",
                "мало времени","времени мало","не успею",
                "не успеваю","успею ли","мне не хватит"),
            (m,l,s,n) -> {
                if (has(l,"доп","продли","подождать"))
                    return EngineResult.reply("Доп. время не предусмотрено. Скачивай быстрее!");
                if (has(l,"не успею","не успеваю","не хватит"))
                    return EngineResult.reply("Успеешь! AnyDesk качается за минуту");
                return EngineResult.reply(pick(
                    "Осталось " + s.getRemainingMinutes() + " мин",
                    "У тебя " + s.getRemainingMinutes() + " минут"
                ));
            }));

        // ===================== 65: WAIT =====================
        rules.add(new ResponseRule("wait", 65,
            (m,l,s,n) -> {
                String t = l.trim();
                return eq(t,"ща","щас","сек","секу","щя","щяс","шас","минуту","минутку","подожди") ||
                    has(l,"подожд","погод","чуть чуть","жди","щаща","щас сек","ок щас","ша сек",
                        "дай минут","дай секунд","пару секунд","пару минут");
            },
            (m,l,s,n) -> EngineResult.reply(pick(
                "Жду. Осталось " + s.getRemainingMinutes() + " мин",
                "Ок, жду",
                "Давай, не тяни"
            ))));

        // ===================== 64: CONFESSION QUESTION =====================
        rules.add(new ResponseRule("conf_q", 64,
            (m,l,s,n) -> has(l,"какое признание","что за признание",
                "на скок меньше","на сколько забаните","сколько бан","а скок целый",
                "какой бан","сколько дней","на сколько банят",
                "а если признаюсь","а что если признаюсь",
                "что будет если признаюсь","что за наказание",
                "какое наказание"),
            (m,l,s,n) -> {
                s.offeredConfession = true;
                if (has(l,"какое признание","что за признание"))
                    return EngineResult.reply("Признание в использовании читов/софта. Напиши \"Я чит\" для признания");
                return EngineResult.reply("Признание = 20 дней бана. Отказ = 30 дней бана. Напиши \"Я чит\" или \"Отказ\"");
            }));

        // ===================== 63: ACCEPT REQUEST =====================
        rules.add(new ResponseRule("accept", 63,
            (m,l,s,n) -> has(l,"принял","я принял","как принять",
                "приинимать","нет кнопки","не пришло","от имени","от кого",
                "не приходит","нет запроса","запрос не приходит",
                "не вижу запрос","где кнопка","как принять запрос",
                "куда нажать чтобы принять"),
            (m,l,s,n) -> {
                if (has(l,"как принять","нет кнопки","где кнопка","куда нажать"))
                    return EngineResult.reply("В AnyDesk появится окно - нажми зеленую кнопку Принять");
                if (has(l,"от имени","от кого"))
                    return EngineResult.reply("Любой запрос - принимай");
                if (has(l,"не пришло","не приходит","нет запроса","не вижу"))
                    return EngineResult.reply("Скинь код еще раз. Проверь что AnyDesk открыт");
                return EngineResult.reply(pick(
                    "Не трогай мышку и клавиатуру",
                    "Пред 1/3. Не трогай мышку"
                ));
            }));

        // ===================== 62: REGISTRATION =====================
        rules.add(new ResponseRule("reg", 62,
            (m,l,s,n) -> has(l,"регаюсь","регаться","регистрац","зарегаю",
                "нужно регистрироваться","надо регистрироваться",
                "просит регистрацию","требует регистрацию"),
            (m,l,s,n) -> EngineResult.reply("Не надо регистрироваться! Просто скачай, открой и скинь код")));

        // ===================== 61: MINIMAP =====================
        rules.add(new ResponseRule("minimap", 61,
            (m,l,s,n) -> has(l,"миникарта","минимап","пульс это",
                "можно с миникартой","миникарта разрешена",
                "у меня карта","у меня мини карта"),
            (m,l,s,n) -> {
                if (has(l,"разрешен","можно")) return EngineResult.reply("Миникарта разрешена");
                return EngineResult.reply("Миникарта - не читы");
            }));

        // ===================== 60: REPORT ANOTHER =====================
        rules.add(new ResponseRule("report", 60,
            (m,l,s,n) -> has(l,"тут один читер","тут читер",
                "могу дать его ник","против меня софтер",
                "он читерит","а вот он читер","того проверь",
                "его проверь","проверь лучше его"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Напиши /cr ник - мы проверим. А сейчас качай аник",
                "Сейчас речь о тебе. Жалобу на него пиши через /cr ник"
            ))));

        // ===================== 59: BRIBE =====================
        rules.add(new ResponseRule("resources", 59,
            (m,l,s,n) -> has(l,"можно ресы","ресы раздам",
                "можно баблко","деньги отдам","тимейту деньги","можно кинуть",
                "могу заплатить","дам денег","дать денег",
                "кину донат","задоначу","кину ресы",
                "хочешь ресы","хочеш ресы"),
            (m,l,s,n) -> EngineResult.reply("Нет. Качай AnyDesk и проходи проверку")));

        // ===================== 58: LEGAL =====================
        rules.add(new ResponseRule("legal", 58,
            (m,l,s,n) -> has(l,"не законно","незаконно","не доверяю",
                "родительский контроль","не имеете права",
                "это нарушение","мои права","моих прав",
                "персональные данные","защита данных",
                "я тебе не доверяю","я не доверяю"),
            (m,l,s,n) -> {
                if (has(l,"родительский"))
                    return EngineResult.reply("Проси разрешение у родителей. AnyDesk все равно нужен");
                if (has(l,"не доверяю"))
                    return EngineResult.reply("AnyDesk можно отключить в любой момент. Я только посмотрю папки");
                return EngineResult.reply("Заходя на сервер, ты принимаешь правила. Проверка - обязательная процедура");
            }));

        // ===================== 57: FROM RF =====================
        rules.add(new ResponseRule("rf", 57,
            (m,l,s,n) -> has(l,"я из рф","я с рф","из рф","с рф","из россии",
                "аник не ворк на территории","заблокирован в рф",
                "заблокирован в россии","не работает в рф",
                "в россии не работает","в рф не работает",
                "запрещен в рф","запрещён в рф"),
            (m,l,s,n) -> {
                s.mentionedRustdesk = true;
                return EngineResult.reply(pick(
                    "Скачивай RustDesk - работает в РФ без VPN! Сайт: rustdesk com",
                    "Качай RustDesk - rustdesk com. Работает в РФ"
                ));
            }));

        // ===================== 56: VPN =====================
        rules.add(new ResponseRule("vpn", 56,
            (m,l,s,n) -> has(l,"впн","vpn","кикнет","нужен впн"),
            (m,l,s,n) -> EngineResult.reply("Тогда скачивай RustDesk - работает без VPN. rustdesk com")));

        // ===================== 55: PREVIOUS CHECK =====================
        rules.add(new ResponseRule("prev_check", 55,
            (m,l,s,n) -> has(l,"меня проверяли","уже проверяли",
                "вчера проверял","проверяли сегодня",
                "сегодня проверяли","час назад проверяли",
                "меня уже","только что проверяли"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Проверю еще раз. Жду AnyDesk",
                "Повторная проверка - нормальная практика. Качай аник"
            ))));

        // ===================== 54: PAID =====================
        rules.add(new ResponseRule("paid", 54,
            (m,l,s,n) -> has(l,"платная","платный","евро надо","бесплатн",
                "стоит денег","за деньги","нужно платить",
                "просит оплату","просит деньги"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "AnyDesk бесплатный для домашнего использования!",
                "На сайте anydesk com выбирай для домашнего использования - это бесплатно"
            ))));

        // ===================== 53: LINUX / MAC =====================
        rules.add(new ResponseRule("linux_mac", 53,
            (m,l,s,n) -> has(l,"линукс","linux","убунту","ubuntu",
                "макос","macos","мак ос","mac os","у меня мак",
                "у меня линукс","на линуксе","на маке"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "AnyDesk работает на Linux и macOS. Качай с anydesk com",
                "AnyDesk есть под любую ОС. Скачивай с anydesk com"
            ))));

        // ===================== 52: YES =====================
        rules.add(new ResponseRule("yes", 52,
            (m,l,s,n) -> {
                String t = l.trim();
                return eq(t,"да","да?","+","ок","окей","ладно",
                    "хорошо","понял","пон","угу","ну","ага",
                    "da","ladno","ну ок","тогда ок","ясно",
                    "понятно","понел","поняв","поня","ясн",
                    "ладушки","договорились","принял","ок сек");
            },
            (m,l,s,n) -> {
                if (!s.askedForAnydesk) {
                    s.askedForAnydesk = true;
                    return EngineResult.reply("У тебя " + s.getRemainingMinutes() + " мин. Качай AnyDesk - anydesk com");
                }
                if (s.gaveCodes) return EngineResult.reply("Принимай запрос!");
                return EngineResult.reply(pick("Жду", "+", "Давай"));
            }));

        // ===================== 51: QUESTION MARKS =====================
        rules.add(new ResponseRule("qmarks", 51,
            (m,l,s,n) -> l.trim().matches("[?!]+"),
            (m,l,s,n) -> {
                if (s.messageCount <= 2) return EngineResult.reply("Проверка на читы! Скачивай AnyDesk - anydesk com");
                return EngineResult.reply("Жду AnyDesk");
            }));

        // ===================== 50: SHORT =====================
        rules.add(new ResponseRule("short", 50,
            (m,l,s,n) -> {
                String t = l.trim();
                return eq(t,"аник","аник?","кидай",
                    "ну че","ну чо","го","go",
                    "вот","на","это?","это",
                    "ало","ау","аууу","але",
                    "модер","ты тут","ты тут?",
                    "ты здесь","ты здесь?",
                    "эй","хей","hey","ну","ну?",
                    "ну давай","ну и");
            },
            (m,l,s,n) -> {
                String t = l.trim();
                if (has(t,"аник")) return EngineResult.reply("Жду код из AnyDesk");
                if (has(t,"кидай")) return EngineResult.reply("Кидай код (цифры из AnyDesk) мне в лс");
                if (has(t,"вот","на","это")) return EngineResult.reply("Принимай запрос");
                if (has(t,"ты тут","ты здесь","ало","ау","модер","але","эй","хей"))
                    return EngineResult.reply(pick("Да, я тут. Жду аник", "Я здесь. Жду AnyDesk"));
                return EngineResult.reply(pick("Жду AnyDesk", "+"));
            }));

        // ===================== 48: WEAK PC =====================
        rules.add(new ResponseRule("weak_pc", 48,
            (m,l,s,n) -> has(l,"пк слаб","комп слаб","интернет слаб",
                "инет говно","пк за 15к","медленно","лагает",
                "слабый комп","слабый пк","тормозит","тормоза",
                "фпс","fps","пинг","пинг высокий","интернет плохой",
                "инет плохой","скорость маленькая"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Жду! AnyDesk легкая программа. " + s.getRemainingMinutes() + " мин осталось",
                "AnyDesk весит мало - скачается быстро"
            ))));

        // ===================== 47: AT SCHOOL =====================
        rules.add(new ResponseRule("at_school", 47,
            (m,l,s,n) -> has(l,"я на работе","я на уроке","я в школе",
                "я на учёбе","я на учебе","я на паре",
                "у меня урок","у меня пара","я на занятии",
                "мне некогда","я занят","я занята"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Проверка обязательна. У тебя " + s.getRemainingMinutes() + " мин",
                "Время идет. Качай аник с телефона если что"
            ))));

        // ===================== 46: EMOTIONAL =====================
        rules.add(new ResponseRule("emotional", 46,
            (m,l,s,n) -> {
                String t = l.trim();
                return t.matches("[)(]+") ||
                    eq(t,"хаха","хахаха","ахахах","xd","найс","круто","прикольно","гг","лол",
                       "ахах","кек","лмао","лмфао","ржу","ору","орууу","жиза");
            },
            (m,l,s,n) -> {
                if (has(l,"хаха","ахах","xd","кек","лол","ржу","ору"))
                    return EngineResult.reply("После проверки посмеемся) Качай аник");
                return EngineResult.reply(pick("Жду аник", "Признание уменьшает срок на 35%"));
            }));

        // ===================== 45: STALLING =====================
        rules.add(new ResponseRule("stalling", 45,
            (m,l,s,n) -> has(l,"я в дубае","расказу","поговорим",
                "забаниш я ночь","в подушку плакать","мне пизда","я девка",
                "давай поболтаем","можно поговорить",
                "расскажу историю","хочу рассказать",
                "давай потом","давай завтра","можно завтра",
                "давай позже","потом пройду","завтра пройду",
                "сейчас не могу","не сейчас"),
            (m,l,s,n) -> {
                if (has(l,"мне пизда")) {
                    s.offeredConfession = true;
                    return EngineResult.reply("Признание уменьшает бан на 35% (20 вместо 30 дней). Напиши \"Я чит\"");
                }
                if (has(l,"потом","завтра","позже","не сейчас"))
                    return EngineResult.reply("Проверка сейчас! Потом нельзя");
                return EngineResult.reply(pick(
                    "Не тяни время. AnyDesk или признание",
                    "Бро, время идет! Качай аник!"
                ));
            }));

        // ===================== 44: AUTO CONFESSION PROMPT =====================
        rules.add(new ResponseRule("auto_conf", 44,
            (m,l,s,n) -> s.messageCount > 5 && !s.offeredConfession && s.getElapsedMinutes() >= 3,
            (m,l,s,n) -> {
                s.offeredConfession = true;
                return EngineResult.reply(pick(
                    "Напоминаю: признание = 20 дней, отказ = 30 дней. Напиши \"Я чит\" или \"Отказ\"",
                    "Чтоб время не тратить - можешь признаться. Напиши \"Я чит\""
                ));
            }));

        // ===================== 43: NO =====================
        rules.add(new ResponseRule("no", 43,
            (m,l,s,n) -> {
                String t = l.trim();
                return eq(t,"нет","не","неа","нее","не-а","нет?") || t.startsWith("нееее");
            },
            (m,l,s,n) -> EngineResult.reply(pick(
                "Тогда жду AnyDesk",
                "Скачивай аник. Время идет!"
            ))));

        // ===================== 42: FRIEND / BROTHER =====================
        rules.add(new ResponseRule("friend", 42,
            (m,l,s,n) -> has(l,"это мой друг","это мой брат","играет брат",
                "играет друг","за компом брат","за пк брат",
                "за компом сестра","это не я","меня не было",
                "играл не я","брат играл","друг играл",
                "я дал другу","я дал брату"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Ответственность на владельце аккаунта. Качай AnyDesk",
                "Проверяем аккаунт, не человека. Жду аник"
            ))));

        // ===================== 41: MODS =====================
        rules.add(new ResponseRule("mods", 41,
            (m,l,s,n) -> has(l,"у меня моды","у меня мод","я с модами",
                "у меня текстурпак","у меня шейдер","у меня оптифайн",
                "оптифайн это чит","мод это чит","моды разрешены"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Разрешенные моды - не проблема. Скачивай AnyDesk и пройди проверку",
                "Если моды легальные - проверку пройдешь легко"
            ))));

        // ===================== 40: TRANSLIT =====================
        rules.add(new ResponseRule("translit", 40,
            (m,l,s,n) -> has(l,"vse bani","i skacat ne mogy","togda idi v pizdy",
                "kak skachat","gde skachat","ya ne chiter",
                "ya chist","privet"),
            (m,l,s,n) -> {
                if (has(l,"pizdy","bani")) return EngineResult.reply("Оскорбления не помогут. Качай аник");
                if (has(l,"privet")) return EngineResult.reply("Привет! Жду AnyDesk - anydesk com");
                return EngineResult.reply("Ты сможешь! anydesk com");
            }));

        // ===================== 38: CONNECTION =====================
        rules.add(new ResponseRule("conn", 38,
            (m,l,s,n) -> has(l,"клиент не в сети","не подключается",
                "соединение заверш","не воркает","кинь еще раз",
                "не коннектит","отключился","вылетел",
                "сбросилось","разорвано","таймаут"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Попробуй RustDesk - rustdesk com",
                "Скинь код еще раз. Если не работает - качай RustDesk",
                "Переустанови AnyDesk и скинь новый код"
            ))));

        // ===================== 35: ENGLISH =====================
        rules.add(new ResponseRule("english", 35,
            (m,l,s,n) -> has(l,"всё на англ","все на англ","на английском",
                "не понимаю англ","я не знаю англ"),
            (m,l,s,n) -> EngineResult.reply("Просто найди длинное число и скинь мне. У тебя " + s.getRemainingMinutes() + " мин")));

        // ===================== 30: PLUGIN =====================
        rules.add(new ResponseRule("plugin", 30,
            (m,l,s,n) -> has(l,"плагин","plugin","ad1","три линии","полный доступ",
                "нет доступа","нет полного доступа","ограниченный доступ"),
            (m,l,s,n) -> EngineResult.reply("В AnyDesk слева сверху три линии - Настройки - Плагин AD1 - Активировать!")));

        // ===================== 28: DELETE AFTER =====================
        rules.add(new ResponseRule("delete_after", 28,
            (m,l,s,n) -> has(l,"потом удалить","удалить потом","можно удалить",
                "а потом удалю","удалю после","удалить после проверки"),
            (m,l,s,n) -> EngineResult.reply("Да, после проверки можешь удалить AnyDesk")));

        // ===================== 27: SIZE =====================
        rules.add(new ResponseRule("size", 27,
            (m,l,s,n) -> has(l,"сколько весит","скок весит","сколько места",
                "много весит","тяжёлый","тяжелый","памяти"),
            (m,l,s,n) -> EngineResult.reply("AnyDesk весит всего 5 МБ. Скачается за секунды!")));

        // ===================== 25: DONE =====================
        rules.add(new ResponseRule("done", 25,
            (m,l,s,n) -> has(l,"я прошел","я прошёл","спасибо","спс",
                "благодарю","спасиб","пасиб","сяп","сенкс","thanks",
                "спс за проверку","всё?","все?","проверка окончена"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Проверка пройдена! Играй честно",
                "Чисто! Приятной игры на HolyWorld",
                "Пред 1/3. Не трогай мышку"
            ))));

        // ===================== 24: CAN PLAY =====================
        rules.add(new ResponseRule("can_play", 24,
            (m,l,s,n) -> has(l,"можно играть","можно идти","я свободен",
                "я могу идти","могу играть","я свободна",
                "это всё","это все",
                "проверка закончена","мы закончили"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Да, свободен! Приятной игры",
                "Еще секунду... Не трогай мышку"
            ))));

        // ===================== 20: TRYING =====================
        rules.add(new ResponseRule("trying", 20,
            (m,l,s,n) -> has(l,"попробую","постараюсь","я тут",
                "запускаю","открыл","открываю","лан",
                "я тута","запустил","включил","включаю",
                "я пытаюсь","щас сделаю","уже делаю"),
            (m,l,s,n) -> EngineResult.reply(pick(
                "Жду",
                "Ок, " + s.getRemainingMinutes() + " мин",
                "Давай!"
            ))));

        // ===================== 15: INSTRUCTION =====================
        rules.add(new ResponseRule("full_instruction", 15,
            (m,l,s,n) -> has(l,"инструкция","как пройти проверку",
                "что нужно для проверки","объясни","расскажи как"),
            (m,l,s,n) -> EngineResult.reply("1. Открой браузер, зайди на anydesk com. 2. Скачай и открой AnyDesk. 3. Найди свой код (Рабочее место / Ваш ID). 4. Скинь код мне в лс. 5. Прими запрос подключения")));

        // ===================== 10: CATCHALL =====================
        rules.add(new ResponseRule("catchall", 10,
            (m,l,s,n) -> true,
            (m,l,s,n) -> {
                if (s.messageCount <= 1) {
                    s.askedForAnydesk = true;
                    return EngineResult.reply("Это проверка на читы! У тебя 7 минут чтобы скачать AnyDesk и пройти проверку! Признание уменьшает наказание! Отказ/выход/игнор = Бан!");
                }
                if (s.messageCount > 8 && !s.offeredConfession) {
                    s.offeredConfession = true;
                    return EngineResult.reply("Напоминаю: признание = 20 дней, отказ = 30 дней. Напиши \"Я чит\" или \"Отказ\"");
                }
                if (!s.warnedAboutTime && s.getElapsedMinutes() >= 5) {
                    s.warnedAboutTime = true;
                    return EngineResult.reply("Внимание! Осталось " + s.getRemainingMinutes() + " мин! Качай AnyDesk сейчас!");
                }
                return EngineResult.reply(pick(
                    "Жду AnyDesk",
                    "Скачивай AnyDesk - anydesk com",
                    "Аник жду! " + s.getRemainingMinutes() + " мин осталось",
                    "Время идет. Качай AnyDesk"
                ));
            }));

        rules.sort((a,b) -> Integer.compare(b.pri, a.pri));
    }

    public EngineResult getResult(String playerMessage, String playerName) {
        if (playerMessage == null || playerMessage.trim().isEmpty()) return null;

        String lower = playerMessage.toLowerCase().trim();
        PlayerState state = playerStates.computeIfAbsent(playerName, k -> new PlayerState());
        state.messageCount++;

        for (ResponseRule rule : rules) {
            try {
                if (rule.matcher.test(playerMessage, lower, state, playerName)) {
                    EngineResult result = rule.responder.get(playerMessage, lower, state, playerName);
                    state.lastCategory = rule.cat;
                    if (result != null) {
                        HolyWorldAutoReply.LOGGER.info("[Engine] [{}] '{}' -> action={} msg='{}'",
                            rule.cat, playerMessage, result.action, result.message);
                    }
                    return result;
                }
            } catch (Exception e) {
                HolyWorldAutoReply.LOGGER.error("[Engine] Error {}: {}", rule.cat, e.getMessage());
            }
        }
        return null;
    }

    // Legacy support
    public String getResponse(String playerMessage, String playerName) {
        EngineResult r = getResult(playerMessage, playerName);
        return r != null ? r.message : null;
    }

    public void clearPlayerState(String n) { playerStates.remove(n); }
    public void clearAllStates() { playerStates.clear(); }
}
