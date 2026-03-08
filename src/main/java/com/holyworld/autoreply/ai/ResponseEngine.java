package com.holyworld.autoreply.ai;

import com.holyworld.autoreply.HolyWorldAutoReply;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ResponseEngine {

    private final ConcurrentHashMap<String, PlayerState> states = new ConcurrentHashMap<>();
    private final List<Rule> rules = new ArrayList<>();

    public ResponseEngine() {
        buildRules();
        rules.sort((a, b) -> Integer.compare(b.priority, a.priority));
        HolyWorldAutoReply.LOGGER.info("[Engine] {} правил", rules.size());
    }

    public static class PlayerState {
        public long startTime = System.currentTimeMillis();
        public int msgCount = 0;
        public boolean sentGreeting = false;
        public boolean askedForProgram = false;
        public boolean gaveCodes = false;
        public boolean useRustdesk = false;
        public boolean warnedRefusal = false;
        public boolean warnedConfession = false;
        public boolean offeredConfession = false;
        public boolean warnedTime = false;
        public boolean awaitingResponse = false;
        public String lastCat = "";

        public int remaining() { return Math.max(1, 7 - (int)((System.currentTimeMillis() - startTime) / 60000)); }
        public long elapsed() { return (System.currentTimeMillis() - startTime) / 60000; }
        public String prog() { return useRustdesk ? "RustDesk" : "AnyDesk"; }
        public String site() { return useRustdesk ? "rustdesk com" : "anydesk com"; }
    }

    public static class Result {
        public enum Action { REPLY, BAN_INSULT, BAN_REFUSAL, BAN_CONFESSION }
        public final Action action;
        public final String message;
        private Result(Action a, String m) { this.action = a; this.message = m; }
        public static Result reply(String m) { return new Result(Action.REPLY, m); }
        public static Result banInsult() { return new Result(Action.BAN_INSULT, null); }
        public static Result banRefusal() { return new Result(Action.BAN_REFUSAL, null); }
        public static Result banConfession() { return new Result(Action.BAN_CONFESSION, null); }
    }

    @FunctionalInterface private interface Match { boolean test(String m, String l, PlayerState s); }
    @FunctionalInterface private interface Reply { Result get(String m, String l, PlayerState s); }
    private static class Rule {
        final String cat; final int priority; final Match match; final Reply reply;
        Rule(String c, int p, Match m, Reply r) { cat=c; priority=p; match=m; reply=r; }
    }

    private static String pick(String... o) { return o[ThreadLocalRandom.current().nextInt(o.length)]; }
    private static boolean has(String t, String... kw) { for (String k : kw) if (t.contains(k)) return true; return false; }
    private static boolean eq(String t, String... vals) { String tr = t.trim(); for (String v : vals) if (tr.equals(v)) return true; return false; }

    /**
     * Нормализация текста для поиска оскорблений.
     * Убирает пробелы, точки, дефисы, подчёркивания, звёздочки между буквами,
     * заменяет латиницу-двойники на кириллицу, цифры на буквы.
     */
    private static String normalize(String input) {
        if (input == null) return "";
        String s = input.toLowerCase();

        // Убираем символы-разделители между буквами
        s = s.replaceAll("[\\s\\-_.*+#@!,;:()\\[\\]{}|/\\\\~`'\"<>^&%$]+", "");

        // Замена латиницы-двойников на кириллицу
        s = s.replace('a', 'а');
        s = s.replace('e', 'е');
        s = s.replace('o', 'о');
        s = s.replace('p', 'р');
        s = s.replace('c', 'с');
        s = s.replace('x', 'х');
        s = s.replace('y', 'у');
        s = s.replace('k', 'к');
        s = s.replace('m', 'м');
        s = s.replace('t', 'т');
        s = s.replace('b', 'б');
        s = s.replace('u', 'у');
        s = s.replace('h', 'х');
        s = s.replace('n', 'н');
        s = s.replace('r', 'р');
        s = s.replace('i', 'и');
        s = s.replace('d', 'д');
        s = s.replace('l', 'л');
        s = s.replace('s', 'с');
        s = s.replace('v', 'в');
        s = s.replace('z', 'з');
        s = s.replace('g', 'г');
        s = s.replace('f', 'ф');
        s = s.replace('w', 'ш');
        s = s.replace('j', 'й');
        s = s.replace('q', 'к');

        // Замена цифр на похожие буквы
        s = s.replace('0', 'о');
        s = s.replace('3', 'з');
        s = s.replace('4', 'ч');
        s = s.replace('6', 'б');
        s = s.replace('9', 'д');
        s = s.replace('1', 'и');

        // ё -> е
        s = s.replace('ё', 'е');

        return s;
    }

    /**
     * Проверяет содержит ли нормализованный текст любое из ключевых слов.
     */
    private static boolean hasNorm(String normalized, String... kw) {
        for (String k : kw) if (normalized.contains(k)) return true;
        return false;
    }

    private void buildRules() {

        // ==================== 500: ПРЯМОЙ ОТКАЗ ====================

        rules.add(new Rule("ban_refusal_exact", 500,
            (m,l,s) -> eq(l, "отказ", "отказываюсь"),
            (m,l,s) -> Result.banRefusal()));

        // ==================== 495: ПРЯМОЕ ПРИЗНАНИЕ ====================

        rules.add(new Rule("ban_confession_exact", 495,
            (m,l,s) -> eq(l, "я чит", "я читер", "признание"),
            (m,l,s) -> Result.banConfession()));

        // ==================== 490: ПРИЗНАНИЕ С ДОПСЛОВАМИ ====================

        rules.add(new Rule("ban_confession_extended", 490,
            (m,l,s) -> {
                String t = l.trim();
                if (t.startsWith("я чит") && t.length() > 5) return true;
                if (t.startsWith("я читер") && t.length() > 7) return true;
                return false;
            },
            (m,l,s) -> Result.banConfession()));

        // ==================== 400: ОСКОРБЛЕНИЕ МОДЕРАТОРА ====================
        // Теперь с нормализацией — ловит любые варианты написания,
        // латиницу, пробелы между буквами, цифры вместо букв и т.д.

        rules.add(new Rule("insult_moderator", 400,
            (m,l,s) -> {
                String norm = normalize(l);

                // === ПОСЫЛАНИЕ (всегда бан — это прямое оскорбление) ===

                // "пошёл/пошел/пашол/пашел нахуй/нах/нахер/нафиг" и все варианты
                boolean sending = hasNorm(norm,
                    "пошелнах", "пошолнах", "пашолнах", "пашелнах",
                    "пошелнахуй", "пошолнахуй", "пашолнахуй", "пашелнахуй",
                    "пошелнахуи", "пошолнахуи", "пашолнахуи", "пашелнахуи",
                    "пошелнахер", "пошолнахер", "пашолнахер", "пашелнахер",
                    "пошелвжопу", "пошолвжопу", "пашолвжопу",
                    "идинах", "идинахуй", "идинахуи", "идинахер",
                    "идивжопу", "идикчерту",
                    "катись", "вали", "валинах", "валиотсюда",
                    "убирайся", "свали", "сваливай", "свалинах",
                    "отвали", "отвалинах", "отвянь",
                    "пшелнах", "пшолнах"
                );
                if (sending) return true;

                // === "СОСИ" — всегда оскорбление ===
                if (hasNorm(norm, "соси", "сосимой", "сосихуй", "пососи", "отсоси")) return true;

                // === ПРЯМОЕ ОБРАЩЕНИЕ "ТЫ + ОСКОРБЛЕНИЕ" ===
                boolean tyInsult = hasNorm(norm,
                    "тысука", "тымразь", "тыурод", "тыдебил",
                    "тыдаун", "тычмо", "тыпидор", "тыконч",
                    "тыидиот", "тыкретин", "тытварь", "тышлюха",
                    "тыуебок", "тыуебан", "тымудак", "тымудила",
                    "тыкозел", "тыговнюк", "тыскотина",
                    "тыкрыса", "тышакал", "тымусор", "тыублюд",
                    "тывыродок", "тыдолбаеб", "тыдолбоеб",
                    "тыгандон", "тыгондон", "тыхуесос", "тыпедик",
                    "тычмошник", "тылох", "тылошара", "тынуб",
                    "тыникчемный", "тыжалкий", "тытупой",
                    "тыбыдло", "тыотстой", "тыничтожество",
                    "тыпидар", "тыпидарас",
                    "тыебанат", "тыебанутый", "тыебаный",
                    "тыблядь", "тыбляд", "тышалава",
                    "тыпиздабол", "тыпиздюк",
                    "тыхуйло", "тыхуила"
                );
                if (tyInsult) return true;

                // === МОДЕРАТОР/АДМИН + ОСКОРБЛЕНИЕ ===
                boolean modInsult = hasNorm(norm,
                    "модерсука", "модермразь", "модерурод", "модердебил",
                    "модердаун", "модерчмо", "модерпидор",
                    "модеридиот", "модерлох", "модертупой",
                    "модерхуесос", "модерконч", "модерублюд",
                    "админсука", "админмразь", "админурод", "админдебил",
                    "админдаун", "админчмо", "админпидор",
                    "админидиот", "админлох", "админтупой",
                    "проверяющийсука", "проверяющиймразь", "проверяющийдебил"
                );
                if (modInsult) return true;

                // === "ТЕБЯ/ТЕБЕ" + ОСКОРБЛЕНИЕ ===
                boolean tebyaInsult = hasNorm(norm,
                    "тебяебал", "тебяебу", "тебяимел",
                    "тебепизд", "тебянах", "тебяненавижу",
                    "тебявжопу", "тебявзад"
                );
                if (tebyaInsult) return true;

                // === МАТЬ ===
                if (hasNorm(norm,
                    "мамуебал", "мамуимел", "мамутвою",
                    "твоюмать", "твоямать",
                    "мамкуебал", "мамкутвою", "мамкуимел",
                    "матьебал", "матьтвою",
                    "ебалтвоюмать", "ебалтвоюмамку",
                    "ебатьтвоюмать"
                )) return true;

                return false;
            },
            (m,l,s) -> Result.banInsult()));

        // ==================== 355: КОНТЕКСТНЫЙ ОТКАЗ ====================

        rules.add(new Rule("contextual_refusal", 355,
            (m,l,s) -> {
                return (eq(l, "нет","не","неа","нее","не буду","нет спасибо") && s.askedForProgram) ||
                    has(l, "не хочу","не буду","не стану","не собираюсь",
                        "мне лень","я отказываюсь от","не буду скачивать",
                        "не буду качать","не хочу скачивать","не хочу качать",
                        "не буду проходить","не хочу проходить","забей",
                        "не буду ничего","забань просто","бань уже",
                        "давай бан","мне пофиг","мне похер",
                        "похуй на бан","плевать на бан","мне все равно",
                        "мне всё равно","забань и всё","не обязан",
                        "я отказ","отвали","не буду я",
                        "бань меня","баньте меня","давай баньте",
                        "ну бань","ну баньте","забань меня");
            },
            (m,l,s) -> {
                if (!s.warnedRefusal) {
                    s.warnedRefusal = true;
                    return Result.reply("Если отказываешься от проверки - напиши \"Отказ\". Это бан на 30 дней.");
                }
                return Result.reply("Последний шанс. Напиши \"Отказ\" для оформления. Бан 30 дней.");
            }));

        // ==================== 305: КОНТЕКСТНОЕ ПРИЗНАНИЕ ====================

        rules.add(new Rule("contextual_confession", 305,
            (m,l,s) -> has(l,
                "я с софтом","у меня софт","у меня читы","да я читер",
                "у меня есть софт","да я софтер","я с читами",
                "ладно я с читами","я юзаю","я использую",
                "у меня x-ray","у меня xray","у меня иксрей",
                "у меня селестиал","у меня celestial",
                "у меня impact","у меня импакт",
                "у меня wurst","у меня вурст",
                "у меня meteor","у меня метеор",
                "у меня rusherhack","у меня рашерхак",
                "у меня aristois","у меня аристоис",
                "у меня baritone","у меня баритон",
                "у меня killaura","у меня киллаура",
                "у меня aimbot","у меня аимбот",
                "у меня autoclicker","у меня автокликер",
                "у меня reach","у меня рич",
                "у меня fly","у меня флай",
                "у меня speed","у меня спид",
                "у меня nuker","у меня нукер",
                "у меня freecam","у меня фрикам",
                "у меня чит клиент","у меня читерский",
                "у меня запрещённый","у меня запрещенный",
                "с софтом бань","хочешь бань","хочеш бань",
                "бань нахуй ладно","ладно я софт",
                "окей я читер","ок я читер",
                "хорошо я признаюсь","я признаюсь",
                "признаюсь что","готов признаться",
                "хочу признаться","могу признаться",
                "го признание","давай признание",
                "давай признаюсь","ладно признаюсь",
                "я читерю","я читерил","я с читом",
                "у меня чит","ладно у меня читы",
                "да у меня софт","да у меня читы",
                "ладно у меня софт","признаю что читер",
                "я чит бань","читер бань","бань читер",
                "я с софтом бань","софт бань","бань за софт"),
            (m,l,s) -> {
                if (!s.warnedConfession) {
                    s.warnedConfession = true;
                    return Result.reply("Для официального признания напиши \"Я чит\". Бан будет 20 дней вместо 30.");
                }
                return Result.reply("Напиши \"Я чит\" для оформления признания. Бан 20 дней.");
            }));

        // ==================== 250: УГРОЗЫ ====================

        rules.add(new Rule("threats", 250,
            (m,l,s) -> has(l, "найду тебя","тебе конец","ты труп","убью","зарежу",
                "взломаю","ддос","ddos","деаноним","сват","свачу","узнаю где жив"),
            (m,l,s) -> Result.reply(pick(
                "Угрозы только ухудшают твоё положение. Качай " + s.prog() + " и проходи проверку.",
                "Мне не страшно. Жду " + s.prog() + ".",
                "Угрозы не отменяют проверку. Осталось " + s.remaining() + " мин."
            ))));

        // ==================== 200: УХОД ====================

        rules.add(new Rule("leaving", 200,
            (m,l,s) -> eq(l.trim(), "бб","bb","бай","пока","выхожу") ||
                has(l, "я ухожу","я выхожу","я выйду","я ливаю","я ливну",
                    "бб всем","бб короче","пока всем","качать не охота",
                    "качать не буду","я жду бан","лад баньте","ладно баньте"),
            (m,l,s) -> {
                if (!s.warnedRefusal) {
                    s.warnedRefusal = true;
                    return Result.reply("Выход = отказ от проверки (30 дней бана). Напиши \"Отказ\" если уверен.");
                }
                return Result.reply("Уход = отказ. Напиши \"Отказ\" для оформления.");
            }));

        // ==================== 150: КОД ====================

        rules.add(new Rule("code", 150,
            (m,l,s) -> m.replaceAll("[^0-9]", "").length() >= 9,
            (m,l,s) -> { s.gaveCodes = true; s.awaitingResponse = false;
                return Result.reply(pick(
                    "Код принят. Подключаюсь. Прими запрос в " + s.prog() + ".",
                    "Принимай запрос подключения. Не трогай мышку и клавиатуру.",
                    "Вижу код. Подключаюсь, нажми 'Принять'.",
                    "Сейчас зайду. Жми 'Принять' когда появится запрос."
                )); }));

        rules.add(new Rule("sent_pm", 145,
            (m,l,s) -> has(l, "в лс скинул","скинул в лс","написал в лс","кинул в лс",
                "отправил в лс","проверь лс","чекни лс","гляди лс","в личку"),
            (m,l,s) -> { s.gaveCodes = true;
                return Result.reply(pick("Вижу. Подключаюсь, прими запрос.", "Принял. Не трогай мышку.", "Сейчас зайду. Нажми 'Принять'.")); }));

        rules.add(new Rule("discord", 140,
            (m,l,s) -> has(l, "через дс","давай дс","го дс","можно дс","по дс",
                "через дискорд","можно дискорд","го дискорд","в звонок","скайп","тимспик","зум","zoom"),
            (m,l,s) -> Result.reply("Нет, проверка только через " + s.prog() + ". Качай с " + s.site() + ".")));

        rules.add(new Rule("vk_tg", 138,
            (m,l,s) -> has(l, "через вк","го вк","можно вк","через тг","го тг","можно тг",
                "демонстрация","стрим","трансляция","могу показать экран"),
            (m,l,s) -> Result.reply("Только через " + s.prog() + ". Стримы и демонстрации не принимаются.")));

        rules.add(new Rule("to_pm", 135,
            (m,l,s) -> has(l, "можно в лс","могу в лс","кому в лс","куда кидать","кому скинуть","куда скинуть","кому код","куда код"),
            (m,l,s) -> Result.reply("Скинь код мне в личные сообщения (ЛС).")));

        rules.add(new Rule("reason", 130,
            (m,l,s) -> has(l, "за что","причина","за что прове","почему вызвал",
                "за что вызвал","почему меня","что я сделал","что случилось",
                "в чем причина","зачем вызвал","за что проверка","что произошло",
                "почему проверка","я ничего не делал","я не нарушал","за что меня"),
            (m,l,s) -> { s.askedForProgram = true; s.awaitingResponse = true;
                return Result.reply(pick(
                    "На тебя поступили жалобы. Качай " + s.prog() + " - " + s.site() + ".",
                    "Причина - репорты. Скачивай " + s.prog() + " (" + s.site() + ").",
                    "Жалобы на подозрительную игру. Качай " + s.prog() + "."
                )); }));

        rules.add(new Rule("just_playing", 128,
            (m,l,s) -> has(l, "я просто играл","я просто копал","я просто строил",
                "я просто бегал","я ничего не делал такого","я просто фармил",
                "я просто стоял","я афк был","я был на спавне","я на спавне",
                "я тока зашёл","я только зашел","я только что зашёл",
                "я просто ходил","я просто крафтил"),
            (m,l,s) -> { s.askedForProgram = true; s.awaitingResponse = true;
                return Result.reply(pick(
                    "Проверка не значит что ты виноват. Качай " + s.prog() + ".",
                    "Понимаю, но на тебя пожаловались. Скачай " + s.prog() + " (" + s.site() + ").",
                    "Скачай " + s.prog() + ", покажи что софта нет - и свободен."
                )); }));

        rules.add(new Rule("what_cheats", 126,
            (m,l,s) -> has(l, "какие читы","что за читы","какой чит","какой софт",
                "что за софт","какие жалобы","на что жалобы","в чём подозревают",
                "а что именно","что конкретно","какие репорты","на что репорт"),
            (m,l,s) -> { s.askedForProgram = true; s.awaitingResponse = true;
                return Result.reply(pick(
                    "Детали не разглашаются. Качай " + s.prog() + ".",
                    "Подробности конфиденциальны. Жду " + s.prog() + ".",
                    "Информация закрыта. Скачивай " + s.prog() + " (" + s.site() + ")."
                )); }));

        rules.add(new Rule("not_cheater", 124,
            (m,l,s) -> has(l, "я не читер","я чист","у меня нет читов","без читов",
                "без софта","я легит","я не юзаю","я чистый","клянусь",
                "честное слово","зуб даю","мамой клянусь","я готов пройти"),
            (m,l,s) -> { s.askedForProgram = true; s.awaitingResponse = true;
                return Result.reply(pick(
                    "Тогда нечего бояться. Скачивай " + s.prog() + " (" + s.site() + ").",
                    "Если чист - пару минут. Качай " + s.prog() + ".",
                    "Докажи. " + s.prog() + ", две минуты, и свободен."
                )); }));

        rules.add(new Rule("what_check", 122,
            (m,l,s) -> has(l, "что будешь смотреть","что проверяешь","что будешь проверять",
                "что именно смотр","что ты ищешь","что надо проверить"),
            (m,l,s) -> Result.reply(pick(
                "Проверю рабочий стол, процессы, папку с игрой.",
                "Посмотрю процессы, файлы Minecraft и загрузки.",
                "Стандартная проверка: процессы, файлы Minecraft, рабочий стол."
            ))));

        rules.add(new Rule("what_anydesk", 120,
            (m,l,s) -> has(l, "что за аник","что такое аник","что за анидеск",
                "что такое анидеск","что за прога","че за прога",
                "анидеск это что","а зачем скачивать","это безопасно","это вирус"),
            (m,l,s) -> {
                if (has(l, "вирус","опасн","безопасн"))
                    return Result.reply(s.prog() + " - официальная безопасная программа. Сайт " + s.site() + ".");
                return Result.reply(s.prog() + " - программа удалённого доступа. Качай с " + s.site() + ".");
            }));

        rules.add(new Rule("where_dl", 118,
            (m,l,s) -> has(l, "где скачать","как скачать","ссылку","ссылка",
                "откуда качать","как установить","где найти","хз где","что скачать","что качать","какую программу"),
            (m,l,s) -> { s.askedForProgram = true; s.awaitingResponse = true;
                return Result.reply("Открой браузер, набери " + s.site() + ". Скачай, запусти, скинь код в ЛС."); }));

        rules.add(new Rule("where_code", 116,
            (m,l,s) -> has(l, "где код","где id","где айди","где цифры","не вижу код","какой код"),
            (m,l,s) -> Result.reply("Код в " + s.prog() + " - 'Ваш адрес' или 'Your ID'. Скинь в ЛС.")));

        rules.add(new Rule("downloading", 114,
            (m,l,s) -> has(l, "скачиваю","качаю","загружаю","устанавливаю",
                "щас скачаю","ща скачаю","уже качаю","жди качаю","грузится","загружается","качается"),
            (m,l,s) -> { s.awaitingResponse = false;
                return Result.reply(pick("Жду. Осталось " + s.remaining() + " мин.", "Хорошо, не затягивай.", "Ок, жду.")); }));

        rules.add(new Rule("downloaded", 112,
            (m,l,s) -> has(l, "скачал","загрузил","установил","скачался","загрузился","готово","всё скачал","уже открыл","запустил","открыл"),
            (m,l,s) -> { s.awaitingResponse = false;
                return Result.reply(pick(
                    "Найди код (Ваш адрес / Your ID) и скинь мне в ЛС.",
                    "Кидай длинное число мне в ЛС.",
                    "Найди цифры (ID) в " + s.prog() + " и скинь мне в ЛС."
                )); }));

        rules.add(new Rule("cant_dl", 110,
            (m,l,s) -> has(l, "не скачивается","не качается","не загружается",
                "не грузит","не могу скачать","не работает","ошибка",
                "не запускается","не открывается","не получается",
                "заблокирован","блокирует","антивирус","зависло"),
            (m,l,s) -> {
                if (!s.useRustdesk) { s.useRustdesk = true;
                    return Result.reply("Попробуй RustDesk. Сайт: rustdesk com."); }
                return Result.reply(pick("Другой браузер или отключи антивирус. rustdesk com.",
                    "Попробуй ещё раз. Осталось " + s.remaining() + " мин."));
            }));

        rules.add(new Rule("phone", 108,
            (m,l,s) -> has(l, "с телефон","на телефоне","с мобил","на андроид","с планшета","с айфона"),
            (m,l,s) -> Result.reply(s.prog() + " есть на телефон! Play Market или App Store.")));

        rules.add(new Rule("rustdesk_request", 106,
            (m,l,s) -> has(l, "растдеск","rustdesk","рустдеск","можно рустдеск","подойдет рустдеск","можно раст"),
            (m,l,s) -> { s.useRustdesk = true; return Result.reply("Да, RustDesk подойдёт! rustdesk com."); }));

        rules.add(new Rule("rf", 104,
            (m,l,s) -> has(l, "из рф","из россии","заблокирован в рф","в рф не работает","запрещен в рф"),
            (m,l,s) -> { s.useRustdesk = true; return Result.reply("Качай RustDesk - работает в РФ. rustdesk com."); }));

        rules.add(new Rule("vpn", 103,
            (m,l,s) -> has(l, "впн","vpn"),
            (m,l,s) -> { s.useRustdesk = true; return Result.reply("Качай RustDesk - без VPN. rustdesk com."); }));

        rules.add(new Rule("ban_questions", 100,
            (m,l,s) -> has(l, "какое признание","что за признание","сколько бан",
                "на сколько забанят","какой бан","сколько дней",
                "а если признаюсь","что будет если признаюсь","какое наказание","на скок бан"),
            (m,l,s) -> { s.offeredConfession = true;
                return Result.reply("Признание = 20 дней. Отказ = 30 дней. \"Я чит\" или \"Отказ\"."); }));

        rules.add(new Rule("simple_no", 90,
            (m,l,s) -> { if (s.awaitingResponse) return false; return eq(l, "нет","не","неа","нее"); },
            (m,l,s) -> Result.reply("Что нет? Качай " + s.prog() + " или напиши \"Отказ\".")));

        rules.add(new Rule("wait", 88,
            (m,l,s) -> eq(l, "ща","щас","сек","минуту","минутку","подожди","секунду") ||
                has(l, "подожд","погод","чуть чуть","пару минут","пару секунд"),
            (m,l,s) -> Result.reply(pick("Жду. " + s.remaining() + " мин.", "Давай быстрее.", "Не затягивай."))));

        rules.add(new Rule("yes", 86,
            (m,l,s) -> eq(l, "да","+","ок","окей","ладно","хорошо","понял","понятно","ясно","угу","ага","пон","лан"),
            (m,l,s) -> { s.awaitingResponse = false;
                if (!s.askedForProgram) { s.askedForProgram = true; return Result.reply("Качай " + s.prog() + " с " + s.site() + ". " + s.remaining() + " мин."); }
                if (s.gaveCodes) return Result.reply("Принимай запрос!");
                return Result.reply(pick("Жду.", "Давай.", "+")); }));

        rules.add(new Rule("time", 84,
            (m,l,s) -> has(l, "сколько времени","сколько минут","сколько осталось","скок времени","скок осталось","доп время","продли время","не успею","не успеваю"),
            (m,l,s) -> { if (has(l,"доп","продли")) return Result.reply("Доп время не предусмотрено."); return Result.reply("Осталось " + s.remaining() + " минут."); }));

        rules.add(new Rule("qmarks", 82, (m,l,s) -> l.trim().matches("[?!?!]+"),
            (m,l,s) -> { if (s.msgCount <= 2) return Result.reply("Проверка на читы. Качай " + s.prog() + " с " + s.site() + ". 7 минут."); return Result.reply("Жду " + s.prog() + "."); }));

        rules.add(new Rule("what_next", 80,
            (m,l,s) -> has(l, "что дальше","что делать","что мне делать","куда жмать","а дальше","дальше что","куда нажать","я не понимаю"),
            (m,l,s) -> { if (s.gaveCodes) return Result.reply("Нажми 'Принять' в " + s.prog() + ".");
                if (s.askedForProgram) return Result.reply("Кидай код из " + s.prog() + " мне в ЛС.");
                return Result.reply("1. " + s.site() + " 2. Скачай 3. Открой 4. Код в ЛС 5. Прими запрос"); }));

        rules.add(new Rule("accepted", 78,
            (m,l,s) -> has(l, "принял","я принял","нет кнопки","не пришло","не приходит","нет запроса","где кнопка","от кого"),
            (m,l,s) -> { if (has(l,"нет кнопки","где кнопка")) return Result.reply("Нажми 'Принять' в " + s.prog() + ".");
                if (has(l,"не пришло","не приходит","нет запроса")) return Result.reply("Скинь код ещё раз.");
                return Result.reply("Не трогай мышку и клавиатуру."); }));

        rules.add(new Rule("no_anik", 76,
            (m,l,s) -> has(l, "нету аник","нет аник","аника нет","анидеска нет","нету программы","не установлен"),
            (m,l,s) -> { s.askedForProgram = true; s.awaitingResponse = true;
                return Result.reply("Скачивай с " + s.site() + ". " + s.remaining() + " мин."); }));

        rules.add(new Rule("reg", 74, (m,l,s) -> has(l, "регистрац","регаться","просит регистрацию"), (m,l,s) -> Result.reply("Регистрация не нужна.")));
        rules.add(new Rule("paid", 72, (m,l,s) -> has(l, "платная","платный","бесплатн","просит оплату"), (m,l,s) -> Result.reply(s.prog() + " бесплатный.")));
        rules.add(new Rule("delete", 70, (m,l,s) -> has(l, "потом удалить","можно удалить","удалю после"), (m,l,s) -> Result.reply("Да, после проверки можешь удалить.")));
        rules.add(new Rule("size", 68, (m,l,s) -> has(l, "сколько весит","много весит"), (m,l,s) -> Result.reply(s.prog() + " весит ~5 МБ.")));
        rules.add(new Rule("plugin", 66, (m,l,s) -> has(l, "плагин","plugin","ad1","полный доступ","нет доступа"), (m,l,s) -> Result.reply("AnyDesk: три линии -> Настройки -> Плагин AD1 -> Активировать.")));
        rules.add(new Rule("english_ui", 64, (m,l,s) -> has(l, "на англ","английском"), (m,l,s) -> Result.reply("Найди длинное число (ID) и скинь мне.")));
        rules.add(new Rule("legal", 62, (m,l,s) -> has(l, "не законно","незаконно","не имеете права","мои права"), (m,l,s) -> Result.reply("Заходя на сервер ты принял правила. Проверка обязательна.")));
        rules.add(new Rule("trust", 60, (m,l,s) -> has(l, "не доверяю","родительский контроль"), (m,l,s) -> { if (has(l,"родительский")) return Result.reply("Попроси разрешение у родителей."); return Result.reply(s.prog() + " можно отключить в любой момент."); }));
        rules.add(new Rule("weak_pc", 58, (m,l,s) -> has(l, "слабый пк","слабый комп","медленно","лагает","тормозит"), (m,l,s) -> Result.reply(s.prog() + " весит 5 МБ.")));
        rules.add(new Rule("prev_check", 56, (m,l,s) -> has(l, "меня проверяли","уже проверяли","вчера проверяли"), (m,l,s) -> Result.reply("Повторная проверка - норма. Качай " + s.prog() + ".")));
        rules.add(new Rule("friend", 54, (m,l,s) -> has(l, "это мой друг","это мой брат","играет брат","это не я","играл не я"), (m,l,s) -> Result.reply("Ответственность на владельце аккаунта.")));
        rules.add(new Rule("mods", 52, (m,l,s) -> has(l, "у меня моды","у меня мод","оптифайн","шейдер"), (m,l,s) -> Result.reply("Разрешённые моды - не проблема. Качай " + s.prog() + ".")));
        rules.add(new Rule("bribe", 50, (m,l,s) -> has(l, "ресы","деньги отдам","могу заплатить","дам денег","кину донат"), (m,l,s) -> Result.reply("Нет. Проверка обязательна.")));
        rules.add(new Rule("stalling", 48, (m,l,s) -> has(l, "давай потом","давай завтра","давай позже","не сейчас","давай поболтаем"), (m,l,s) -> Result.reply("Проверка сейчас.")));
        rules.add(new Rule("minimap", 46, (m,l,s) -> has(l, "миникарта","минимап"), (m,l,s) -> Result.reply("Миникарта разрешена.")));
        rules.add(new Rule("conn", 44, (m,l,s) -> has(l, "не подключается","клиент не в сети","не коннектит","отключился","вылетел"), (m,l,s) -> Result.reply("Скинь код ещё раз.")));
        rules.add(new Rule("busy", 42, (m,l,s) -> has(l, "на работе","на уроке","в школе","на паре","я занят","мне некогда"), (m,l,s) -> Result.reply("Проверка обязательна. " + s.remaining() + " мин.")));
        rules.add(new Rule("here", 40, (m,l,s) -> has(l, "ты тут","ты здесь","ало","ау","модер","але","эй"), (m,l,s) -> Result.reply(pick("Да, я тут. Жду " + s.prog() + ".", "Я здесь. Качай " + s.prog() + "."))));
        rules.add(new Rule("emotional", 38, (m,l,s) -> eq(l.trim(), "хаха","хахаха","xd","лол","кек","ору") || l.trim().matches("[)(]+"), (m,l,s) -> Result.reply("Время идёт. Жду " + s.prog() + ".")));
        rules.add(new Rule("done", 36, (m,l,s) -> has(l, "спасибо","спс","я прошел","я прошёл","благодарю"), (m,l,s) -> Result.reply(pick("Проверка пройдена. Играй честно!", "Чисто! Приятной игры!"))));
        rules.add(new Rule("can_play", 34, (m,l,s) -> has(l, "можно играть","можно идти","я свободен","мы закончили"), (m,l,s) -> Result.reply("Да, свободен!")));
        rules.add(new Rule("trying", 32, (m,l,s) -> has(l, "попробую","постараюсь","запускаю","открываю","включаю","пытаюсь"), (m,l,s) -> { s.awaitingResponse = false; return Result.reply(pick("Жду.", "Давай!")); }));
        rules.add(new Rule("instruction", 30, (m,l,s) -> has(l, "инструкция","как пройти проверку","объясни"), (m,l,s) -> Result.reply("1. " + s.site() + " 2. Скачай 3. Открой 4. ID в ЛС 5. Прими запрос")));
        rules.add(new Rule("linux_mac", 28, (m,l,s) -> has(l, "линукс","linux","макос","macos"), (m,l,s) -> Result.reply(s.prog() + " работает на любой ОС.")));

        // ==================== 15: МАТ БЕЗ НАПРАВЛЕННОСТИ (предупреждение) ====================

        rules.add(new Rule("swearing_warning", 15,
            (m,l,s) -> {
                String norm = normalize(l);
                return hasNorm(norm,
                    "нахуй","нахуи","хуй","хуи","хуе",
                    "ебал","ебан","ебат","ебу","ебаш",
                    "сука","суки","сучка","блядь","бляд",
                    "пизд","шлюха","тварь","говно","говнюк",
                    "мудак","мудила");
            },
            (m,l,s) -> Result.reply(pick("Следи за языком. Качай " + s.prog() + ".", "Без мата. Жду " + s.prog() + "."))));

        // ==================== 10: ПРИВЕТСТВИЕ ====================

        rules.add(new Rule("greeting", 10,
            (m,l,s) -> { if (s.sentGreeting) return false;
                return s.msgCount <= 2 || has(l, "привет","прив","хай","здравств","салам","здаров","приветствую","хелло","hello") || eq(l, "ку","qq","hi","yo"); },
            (m,l,s) -> { s.sentGreeting = true; s.askedForProgram = true; s.awaitingResponse = true;
                return Result.reply(pick(
                    "Привет! Проверка на читы. 7 минут. Качай " + s.prog() + " (" + s.site() + "), кидай код в ЛС. Признание = 20д. Отказ = 30д.",
                    "Добрый день. Проверка на ПО. 7 минут. " + s.prog() + " (" + s.site() + ").",
                    "Привет. Проверка. Качай " + s.prog() + " с " + s.site() + " и скинь ID. 7 минут."
                )); }));

        rules.add(new Rule("auto_confession_offer", 5,
            (m,l,s) -> s.msgCount > 6 && !s.offeredConfession && s.elapsed() >= 3,
            (m,l,s) -> { s.offeredConfession = true;
                return Result.reply("Напоминаю: признание = 20д, отказ = 30д. \"Я чит\" или \"Отказ\"."); }));

        rules.add(new Rule("time_warning", 3,
            (m,l,s) -> !s.warnedTime && s.elapsed() >= 5,
            (m,l,s) -> { s.warnedTime = true;
                return Result.reply("Внимание! " + s.remaining() + " мин! Качай " + s.prog() + " или \"Отказ\"!"); }));

        rules.add(new Rule("default", 0,
            (m,l,s) -> true,
            (m,l,s) -> { if (s.msgCount <= 1) { s.sentGreeting = true; s.askedForProgram = true; s.awaitingResponse = true;
                    return Result.reply("Привет! Проверка на читы. 7 минут. Качай " + s.prog() + " (" + s.site() + ") и код в ЛС."); }
                return Result.reply(pick("Жду " + s.prog() + ".", "Скачивай " + s.prog() + " - " + s.site() + ". " + s.remaining() + " мин.",
                    "Не трать время. Качай " + s.prog() + ".", "Жду код. " + s.remaining() + " мин."
                )); }));
    }

    public Result getResult(String msg, String name) {
        if (msg == null || msg.trim().isEmpty()) return null;
        String low = msg.toLowerCase().trim();
        PlayerState st = states.computeIfAbsent(name, k -> new PlayerState());
        st.msgCount++;
        for (Rule r : rules) {
            try {
                if (r.match.test(msg, low, st)) {
                    Result res = r.reply.get(msg, low, st);
                    st.lastCat = r.cat;
                    HolyWorldAutoReply.LOGGER.info("[AI] [{}] '{}' -> {}", r.cat, msg, res.action);
                    return res;
                }
            } catch (Exception e) {
                HolyWorldAutoReply.LOGGER.error("[AI] {}: {}", r.cat, e.getMessage());
            }
        }
        return null;
    }

    public void clearPlayerState(String n) { states.remove(n); }
    public void clearAllStates() { states.clear(); }
}
