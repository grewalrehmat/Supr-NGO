package in.pact;

import java.util.List;
import java.time.LocalDate;

final class Views {
    private Views() {}

    static String layout(String title, String body, Store.User user) {
        String account = user == null
            ? "<a href='/login'>Sign in</a><a class='button' href='/signup'>Sign up</a>"
            : "<a href='/dashboard'>Dashboard</a><form method='post' action='/logout'><button>Sign out</button></form>";
        return """
            <!doctype html><html lang='en'><head><meta charset='utf-8'>
            <meta name='viewport' content='width=device-width, initial-scale=1'>
            <title>%s | PACT</title><link rel='stylesheet' href='/assets/style.css'></head>
            <body><header><nav aria-label='Main navigation'><a class='brand' href='/'>pact.</a>
            <div class='links'><a href='/projects'>Projects</a><a href='/events'>Events</a>%s</div></nav></header>
            <main><h1>%s</h1>%s</main><footer>PACT · UCS553 Enterprise Web Application</footer></body></html>
            """.formatted(escape(title), account, escape(title), body);
    }

    static String home() {
        return """
            <p class='eyebrow'>People · Action · Community · Transformation</p>
            <p class='intro'>One clear workspace for CSR projects, community events and the people who make them happen.</p>
            <div class='actions'><a class='button' href='/signup'>Join PACT</a>
            <a href='/projects'>Explore CSR projects</a><a href='/login'>Sign in</a></div>
            <section aria-labelledby='workflow'><h2 id='workflow'>A shared workspace for community impact</h2>
            <div class='grid'>
            <article class='card'><span class='card-icon'>01</span><h3>Plan</h3><p>Directors track projects, events and budgets in one place.</p></article>
            <article class='card'><span class='card-icon'>02</span><h3>Coordinate</h3><p>Union heads match volunteer skills and availability to event tasks.</p></article>
            <article class='card'><span class='card-icon'>03</span><h3>Take part</h3><p>Volunteers see their assignments and upcoming community events.</p></article>
            <article class='card'><span class='card-icon'>04</span><h3>Contribute</h3><p>Individuals and companies can support approved projects and community events.</p></article>
            </div></section>
            """;
    }

    static String signup() {
        return """
            <form class='card narrow' method='post' action='/signup'>
            <label>Organisation type<select name='role' required><option value=''>Select a type</option>
            <option value='COMPANY'>Company</option><option value='NGO'>NGO</option>
            <option value='DIRECTOR'>NGO director</option><option value='UNION_HEAD'>Union head</option>
            <option value='VOLUNTEER'>Volunteer</option><option value='INDIVIDUAL'>Individual donor</option></select></label>
            <label>Full name<input name='name' autocomplete='name' maxlength='100' required></label>
            <label>Phone number<input name='phone' type='tel' autocomplete='tel' maxlength='30' required></label>
            <label>Organisation<input name='organisation' autocomplete='organization' maxlength='150' required></label>
            <label>Registration number<input name='registration' maxlength='80' required></label>
            <label>Email<input name='email' type='email' autocomplete='email' maxlength='254' required></label>
            <label>Password<input name='password' type='password' autocomplete='new-password' minlength='8' maxlength='128' required></label>
            <label>Confirm password<input name='confirm' type='password' autocomplete='new-password' required></label>
            <button>Create account</button><p>Already registered? <a href='/login'>Sign in</a></p></form>
            """;
    }

    static String login() {
        return """
            <form class='card narrow' method='post' action='/login'>
            <label>Email<input name='email' type='email' autocomplete='username' required></label>
            <label>Password<input name='password' type='password' autocomplete='current-password' required></label>
            <button>Sign in</button></form>
            """;
    }

    static String projects(List<Store.Project> projects, String theme, String district, String budget, Store.User user) {
        return """
            <form class='card filters' method='get' action='/projects'>
            <label>Theme<input name='theme' value='%s' placeholder='Water'></label>
            <label>District<input name='district' value='%s' placeholder='Patiala'></label>
            <label>Maximum budget (₹)<input name='budget' type='number' min='0' max='999999999999' value='%s'></label>
            <button>Find matches</button></form>
            <p>%d matching projects</p>%s
            """.formatted(escape(theme), escape(district), escape(budget), projects.size(), cards(projects, user));
    }

    static String events(List<Store.Event> events, Store.User user) {
        return "<p class='intro'>Find a community event to support. Contributions are recorded in PACT; no payment is processed.</p>"
            + eventCards(events, false, user, true);
    }

    static String dashboard(Store.User user, List<Store.Project> projects, List<Store.Event> events,
                            List<Store.User> volunteers, List<Store.Task> tasks, long allDonations) {
        String lead = "<p class='intro'>" + escape(user.name()) + " · " + escape(user.organisation())
            + " · " + roleName(user.role()) + "</p>";
        if (user.role() == Store.Role.COMPANY) {
            lead += "<p class='actions'><a class='button' href='/projects'>Explore and support projects</a>"
                + "<a href='/events'>Support community events</a></p><h2>Project progress</h2>";
        } else if (user.role() == Store.Role.NGO) {
            lead += """
                <details class='card narrow'><summary>Propose a project</summary>
                <form method='post' action='/project'>
                <label>Project title<input name='title' maxlength='120' required></label>
                <label>Theme<input name='theme' maxlength='80' required></label>
                <label>District<input name='district' maxlength='80' required></label>
                <label>Funding requested (₹)<input type='number' name='budget' min='1' max='999999999999' required></label>
                <label>Project summary<textarea name='summary' maxlength='1000' required></textarea></label>
                <button>Submit for review</button></form></details><h2>Your projects</h2>
                """;
        } else if (user.role() == Store.Role.DIRECTOR) {
            long planned = events.stream().mapToLong(Store.Event::budget).sum();
            long spent = events.stream().mapToLong(Store.Event::spent).sum();
            lead += "<section class='metrics'><article class='metric'><span>Active projects</span><strong>" + projects.size()
                + "</strong></article><article class='metric'><span>Volunteers</span><strong>" + volunteers.size()
                + "</strong></article><article class='metric'><span>Event budgets</span><strong>₹"
                + money(planned) + "</strong></article><article class='metric'><span>Recorded spend</span><strong>₹"
                + money(spent) + "</strong></article><article class='metric'><span>Total donations</span><strong>₹"
                + money(allDonations) + "</strong></article></section>";
            lead += """
                <div class='section-heading'><div><p class='eyebrow'>Operations</p><h2>Upcoming events</h2></div></div>
                <details class='card narrow'><summary>Create an event</summary><form method='post' action='/event'>
                <label>Event name<input name='title' maxlength='120' placeholder='Community tree planting' required></label>
                <label>Event type<select name='type' required><option>Blood donation</option><option>Plantation</option><option>Cloth donation</option><option>Other</option></select></label>
                <label>Location<input name='location' maxlength='120' required></label>
                <label>Date<input type='date' name='date' required></label>
                <label>Planned budget (₹)<input type='number' name='budget' min='0' max='999999999999' value='0' required></label>
                <label>Event details<textarea name='description' maxlength='1000' required></textarea></label>
                <p class='form-hint'>Add an optional measurable target, such as 10,000 trees planted.</p>
                <label>Progress measure<input name='goalMetric' maxlength='80' placeholder='Trees planted'></label>
                <label>Target amount<input type='number' name='goalTarget' min='1' max='999999999999' placeholder='10000'></label>
                <button>Create event</button></form></details>
                """;
            lead += eventCards(events, true, user, false);
            lead += "<h2>Volunteer directory</h2>" + volunteerCards(volunteers);
            lead += "<h2>Current projects</h2>";
        } else if (user.role() == Store.Role.UNION_HEAD) {
            lead += "<h2>Assign event tasks</h2>" + assignmentForm(events, volunteers);
            lead += "<h2>Volunteer directory</h2>" + volunteerCards(volunteers);
            lead += "<h2>Upcoming events</h2>" + eventCards(events, false, user, false);
            lead += "<h2>Project tracking</h2>";
        } else if (user.role() == Store.Role.VOLUNTEER) {
            lead += "<h2>Your assigned tasks</h2>" + volunteerTaskCards(tasks, events);
            lead += "<h2>Upcoming events</h2>" + eventCards(events, false, user, false);
            return lead;
        } else if (user.role() == Store.Role.INDIVIDUAL) {
            lead += "<p class='intro'>Support a project or community event and see your contribution recorded.</p>"
                + "<h2>CSR projects</h2>" + cards(projects, user)
                + "<h2>Upcoming events</h2>" + eventCards(events, false, user, true);
            return lead;
        } else {
            lead += "<h2>Project review</h2>";
        }
        return lead + cards(projects, user);
    }

    private static String roleName(Store.Role role) {
        return switch (role) {
            case DIRECTOR -> "NGO Director";
            case UNION_HEAD -> "Union Head";
            case VOLUNTEER -> "Volunteer";
            case REVIEWER -> "PACT Reviewer";
            case COMPANY -> "Company";
            case NGO -> "NGO";
            case INDIVIDUAL -> "Individual donor";
        };
    }

    private static String money(long amount) { return String.format(java.util.Locale.US, "%,d", amount); }

    private static String volunteerCards(List<Store.User> volunteers) {
        if (volunteers.isEmpty()) return "<p class='card empty'>No volunteers have joined yet.</p>";
        StringBuilder html = new StringBuilder("<div class='grid'>");
        for (Store.User volunteer : volunteers) html.append("<article class='card volunteer-card'><span class='avatar'>")
            .append(escape(volunteer.name().substring(0, 1).toUpperCase())).append("</span><div><h3>")
            .append(escape(volunteer.name())).append("</h3><p>").append(escape(volunteer.organisation()))
            .append(" · ").append(escape(volunteer.email())).append("</p></div></article>");
        return html.append("</div>").toString();
    }

    private static String assignmentForm(List<Store.Event> events, List<Store.User> volunteers) {
        if (events.isEmpty() || volunteers.isEmpty()) return "<p class='card empty'>Add an event and invite volunteers before assigning tasks.</p>";
        StringBuilder html = new StringBuilder("<form class='card narrow' method='post' action='/task'><label>Event<select name='id' required>");
        for (Store.Event event : events) html.append("<option value='").append(event.id()).append("'>")
            .append(escape(event.title())).append(" · ").append(event.date()).append("</option>");
        html.append("</select></label><label>Volunteer<select name='volunteer' required>");
        for (Store.User volunteer : volunteers) html.append("<option value='").append(escape(volunteer.email())).append("'>")
            .append(escape(volunteer.name())).append("</option>");
        html.append("</select></label><label>Task<input name='title' maxlength='120' required></label>")
            .append("<label>Instructions<textarea name='description' maxlength='500' required></textarea></label><button>Assign task</button></form>");
        return html.toString();
    }

    private static String eventCards(List<Store.Event> events, boolean accounting, Store.User user,
                                     boolean allowDonation) {
        List<Store.Event> visible = events.stream()
            .filter(event -> accounting || !event.date().isBefore(LocalDate.now()) || event.status().equals("ONGOING"))
            .filter(event -> accounting || !event.status().equals("COMPLETED")).toList();
        if (visible.isEmpty()) return "<p class='card empty'>" + (accounting
            ? "No events yet. Create one to get started." : "No upcoming events are scheduled.") + "</p>";
        StringBuilder html = new StringBuilder("<div class='grid'>");
        for (Store.Event event : visible) {
            String statusLabel = switch (event.status()) {
                case "ONGOING" -> "Live now";
                case "COMPLETED" -> "Completed";
                default -> "Upcoming";
            };
            html.append("<article class='card event-card'><p class='eyebrow'>").append(escape(event.type())).append(" · ").append(event.date())
                .append(" <span class='status-pill ").append(event.status().toLowerCase(java.util.Locale.ROOT)).append("'>")
                .append(statusLabel).append("</span></p>");
            if (event.goalTarget() > 0) {
                long displayed = Math.min(event.progress(), event.goalTarget());
                int percent = (int) Math.round(displayed * 100.0 / event.goalTarget());
                html.append("<div class='goal-progress'><div class='progress-heading'><strong>")
                    .append(escape(event.goalMetric())).append("</strong><span>").append(money(event.progress()))
                    .append(" / ").append(money(event.goalTarget())).append(" · ").append(percent).append("%</span></div>")
                    .append("<progress max='").append(event.goalTarget()).append("' value='").append(displayed)
                    .append("' aria-label='").append(escape(event.goalMetric())).append(" progress'></progress></div>");
            }
            html.append("<h3>").append(escape(event.title())).append("</h3><p>").append(escape(event.location()))
                .append("</p><p>").append(escape(event.description())).append("</p><p class='donation-total'>Donated <strong>₹")
                .append(money(event.donated())).append("</strong></p>");
            if (accounting) {
                html.append("<div class='budget-line'><span>Spent ₹").append(money(event.spent())).append(" of ₹")
                    .append(money(event.budget())).append("</span><br><span>Donated ₹").append(money(event.donated()))
                    .append("</span></div><form method='post' action='/expense' class='inline-form'>")
                    .append("<input type='hidden' name='id' value='").append(event.id()).append("'><label>Record expense (₹)<input type='number' name='amount' min='1' max='999999999999' required></label><button>Add expense</button></form>");
            }
            if (user != null && user.role() == Store.Role.UNION_HEAD && !event.status().equals("COMPLETED")) {
                html.append("<form class='progress-update' method='post' action='/event-progress'><input type='hidden' name='id' value='")
                    .append(event.id()).append("'><label>Event status<select name='status'><option value='PLANNED'")
                    .append(event.status().equals("PLANNED") ? " selected" : "").append(">Upcoming</option><option value='ONGOING'")
                    .append(event.status().equals("ONGOING") ? " selected" : "").append(">Ongoing</option><option value='COMPLETED'>Completed</option></select></label>");
                if (event.goalTarget() > 0) html.append("<label>").append(escape(event.goalMetric())).append(" so far<input type='number' name='progress' min='0' max='999999999999' value='")
                    .append(event.progress()).append("' required></label>");
                else html.append("<input type='hidden' name='progress' value='0'>");
                html.append("<button>Update event</button></form>");
            }
            if (allowDonation && isDonor(user)) html.append(donationForm("EVENT", event.id()));
            html.append("</article>");
        }
        return html.append("</div>").toString();
    }

    private static String volunteerTaskCards(List<Store.Task> tasks, List<Store.Event> events) {
        if (tasks.isEmpty()) return "<p class='card empty'>No tasks assigned yet. Check back for updates.</p>";
        StringBuilder html = new StringBuilder("<div class='grid'>");
        for (Store.Task task : tasks) {
            Store.Event event = events.stream().filter(e -> e.id() == task.eventId()).findFirst().orElse(null);
            html.append("<article class='card'><p class='eyebrow'>").append(event == null ? "Event" : escape(event.title()))
                .append(event == null ? "" : " · " + event.date()).append("</p><h3>").append(escape(task.title()))
                .append("</h3><p>").append(escape(task.description())).append("</p></article>");
        }
        return html.append("</div>").toString();
    }

    private static String cards(List<Store.Project> projects, Store.User user) {
        if (projects.isEmpty()) return "<p class='card'>No projects to display.</p>";
        StringBuilder html = new StringBuilder("<div class='grid'>");
        for (Store.Project project : projects) {
            html.append("<article class='card'><h3>").append(escape(project.title())).append("</h3><p>")
                .append(escape(project.organisation())).append(" · ").append(escape(project.theme()))
                .append(" · ").append(escape(project.district())).append("</p><p><strong>₹")
                .append(String.format(java.util.Locale.US, "%,d", project.budget())).append("</strong> · ")
                .append(project.approved() ? "Approved" : "Pending review")
                .append("</p><p>").append(escape(project.summary())).append("</p>");
            if (project.approved()) html.append("<p class='donation-total'>Raised <strong>₹")
                .append(money(project.donated())).append("</strong> of ₹").append(money(project.budget())).append("</p>");
            if (project.approved() && isDonor(user)) html.append(donationForm("PROJECT", project.id()));
            if (user != null) {
                appendMilestones(html, project);
                if (user.role() == Store.Role.REVIEWER && !project.approved()) {
                    html.append(("<form method='post' action='/review'><input type='hidden' name='id' value='%d'>"
                         + "<button>Approve project</button></form>").formatted(project.id()));
                }
                if (user.role() == Store.Role.NGO && project.approved()) {
                    html.append("""
                        <details><summary>Record a milestone</summary>
                        <form method='post' action='/milestone'>
                        <input type='hidden' name='id' value='%d'>
                        <label>Milestone date<input type='date' name='date' required></label>
                        <label>Milestone title<input name='milestone' maxlength='120' required></label>
                        <label>Evidence notes<textarea name='evidence' maxlength='1000' required></textarea></label>
                        <button>Save milestone</button></form></details>
                        """.formatted(project.id()));
                }
            }
            html.append("</article>");
        }
        return html.append("</div>").toString();
    }

    private static boolean isDonor(Store.User user) {
        return user != null && (user.role() == Store.Role.COMPANY || user.role() == Store.Role.INDIVIDUAL);
    }

    private static String donationForm(String targetType, long targetId) {
        return "<form class='donation-form' method='post' action='/donate'><input type='hidden' name='targetType' value='"
            + targetType + "'><input type='hidden' name='id' value='" + targetId
            + "'><label>Donation amount (₹)<input type='number' name='amount' min='1' max='999999999999' required></label>"
            + "<button>Record donation</button><small>Recorded in PACT; no payment is processed.</small></form>";
    }

    private static void appendMilestones(StringBuilder html, Store.Project project) {
        html.append("<h4>Milestones</h4>");
        if (project.milestones().isEmpty()) {
            html.append("<p>No milestones recorded.</p>");
            return;
        }
        html.append("<ul>");
        for (Store.Milestone milestone : project.milestones()) {
            html.append("<li><strong>").append(escape(milestone.title())).append("</strong> · ")
                .append(milestone.date()).append("<br>").append(escape(milestone.evidence())).append("</li>");
        }
        html.append("</ul>");
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
    }

    static final String CSS = """
        :root{color-scheme:light;--ink:#172c25;--muted:#587066;--line:#dce5dd;--paper:#f5f8f4;--accent:#195c45;--wash:#e8f1e9}
        *{box-sizing:border-box}body{margin:0;background:var(--paper);color:var(--ink);font:16px/1.55 system-ui,sans-serif}
        header{border-bottom:1px solid var(--line);background:#ffffffc9}nav,main,footer{max-width:1100px;margin:auto;padding:20px 28px}
        nav,.links,.actions{display:flex;gap:18px;align-items:center;flex-wrap:wrap}nav{justify-content:space-between}
        .links{gap:22px}.links a{text-decoration:none;font-weight:600}a{color:var(--accent)}.brand{font:700 32px Georgia,serif;text-decoration:none;letter-spacing:-.06em}
        main{min-height:75vh;padding-top:52px;padding-bottom:72px}h1,h2{font-family:Georgia,serif;line-height:1.12}
        h1{font-size:clamp(2.25rem,6vw,4.3rem);letter-spacing:-.04em;max-width:760px;margin:0 0 22px}
        h2{margin-top:40px;font-size:1.7rem;letter-spacing:-.025em}h3{margin:0 0 8px}h4{margin-bottom:4px}
        p,li{color:var(--muted)}.intro{font-size:1.18rem;max-width:650px}
        .eyebrow{font-size:.76rem;font-weight:750;letter-spacing:.12em;text-transform:uppercase;color:var(--accent);margin:0 0 8px}
        .actions{margin:26px 0 62px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,250px),1fr));gap:16px}
        .card{background:#fff;border:1px solid var(--line);border-radius:14px;padding:22px;overflow-wrap:anywhere;box-shadow:0 5px 18px #18392a08}
        .card-icon{display:grid;place-items:center;width:40px;height:40px;border-radius:12px;background:var(--wash);color:var(--accent);font-weight:750;margin-bottom:18px}
        .narrow{max-width:520px}.filters{display:flex;flex-wrap:wrap;align-items:end;gap:12px}
        label{display:block;font-weight:600;margin-bottom:12px}.filters label{margin:0;flex:1 1 145px}
        input,select,textarea{display:block;width:100%;font:inherit;padding:9px;border:1px solid #849d8e;border-radius:5px;margin-top:5px}
        textarea{min-height:75px}button,.button{background:var(--accent);color:#fff;border:0;border-radius:7px;
        font:inherit;font-weight:650;padding:10px 15px;cursor:pointer;text-decoration:none;display:inline-block;transition:transform .15s,background .15s}
        button:hover,.button:hover{background:#124733;transform:translateY(-1px)}
        :focus-visible{outline:3px solid #80551b;outline-offset:2px}summary{cursor:pointer;font-weight:650}
        details form{margin-top:18px}.metrics{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin:28px 0 35px}
        .metric{padding:18px 20px;background:var(--wash);border-radius:12px}.metric span,.metric strong{display:block}.metric span{font-size:.86rem;color:var(--muted)}.metric strong{font:700 1.65rem Georgia,serif;color:var(--ink);margin-top:5px}
        .volunteer-card{display:flex;gap:14px;align-items:center}.volunteer-card p{font-size:.9rem;margin:0}.volunteer-card h3{margin:0}.avatar{display:grid;place-items:center;flex:none;width:42px;height:42px;border-radius:50%;background:var(--wash);color:var(--accent);font-weight:750}
        .event-card{border-top:3px solid #8fb69a}.budget-line{padding:10px 0;color:var(--accent);font-weight:700}.inline-form{display:flex;align-items:end;gap:10px;flex-wrap:wrap;border-top:1px solid var(--line);padding-top:14px}.inline-form label{flex:1;margin:0}.inline-form button{align-self:end}.empty{color:var(--muted)}
        .donation-total{padding:9px 0;border-top:1px solid var(--line)}.donation-form{border-top:1px solid var(--line);padding-top:14px;margin-top:14px}.donation-form label{margin-bottom:10px}.donation-form small{display:block;color:var(--muted);margin-top:8px}
        .status-pill{display:inline-block;border-radius:99px;padding:3px 9px;background:#edf0ea;color:var(--muted);font-size:.68rem;letter-spacing:.04em;white-space:nowrap}.status-pill.ongoing{background:#dcf4e4;color:#13693f}.status-pill.completed{background:#e9ece9}
        .goal-progress{margin:18px 0}.progress-heading{display:flex;justify-content:space-between;gap:12px;align-items:baseline;font-size:.9rem}.progress-heading span{color:var(--muted)}progress{width:100%;height:14px;margin-top:8px;accent-color:#23794f}progress::-webkit-progress-bar{background:#e7eee8;border-radius:99px}progress::-webkit-progress-value{background:#2b8b5c;border-radius:99px}progress::-moz-progress-bar{background:#2b8b5c;border-radius:99px}
        .progress-update{border-top:1px solid var(--line);padding-top:14px;margin-top:14px}.progress-update label{margin-bottom:10px}.form-hint{font-size:.9rem;margin:14px 0 8px}
        :focus-visible{outline:3px solid #80551b;outline-offset:2px}footer{border-top:1px solid var(--line);color:var(--muted)}
        @media(max-width:560px){nav,main,footer{padding-left:18px;padding-right:18px}main{padding-top:34px}.actions{margin-bottom:44px}}
        """;
}
