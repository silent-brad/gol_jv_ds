package gameoflife;

import com.hubspot.jinjava.Jinjava;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import starfederation.datastar.adapters.response.HttpServletResponseAdapter;
import starfederation.datastar.events.PatchElements;
import starfederation.datastar.utils.ServerSentEventGenerator;

@Controller
public class GameController {

  private final GameService gameService;
  private final Jinjava jinjava = new Jinjava();
  private final String indexTemplate;

  public GameController(GameService gameService) throws IOException {
    this.gameService = gameService;
    try (InputStream is =
        getClass().getClassLoader().getResourceAsStream("templates/index.jinja")) {
      this.indexTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @GetMapping("/")
  @ResponseBody
  public String index() {
    Map<String, Object> model = new HashMap<>();
    model.put("boardHtml", gameService.getCachedBoardHtml());
    return jinjava.render(indexTemplate, model);
  }

  @GetMapping("/sse")
  public void sse(HttpServletRequest request, HttpServletResponse response) throws Exception {
    AsyncContext asyncContext = request.startAsync();
    asyncContext.setTimeout(0);

    CountDownLatch closeLatch = new CountDownLatch(1);

    asyncContext.addListener(
        new AsyncListener() {
          @Override
          public void onComplete(AsyncEvent event) {
            closeLatch.countDown();
          }

          @Override
          public void onTimeout(AsyncEvent event) {
            closeLatch.countDown();
            try {
              event.getAsyncContext().complete();
            } catch (Exception ignored) {
            }
          }

          @Override
          public void onError(AsyncEvent event) {
            closeLatch.countDown();
            try {
              event.getAsyncContext().complete();
            } catch (Exception ignored) {
            }
          }

          @Override
          public void onStartAsync(AsyncEvent event) {}
        });

    var responseAdapter = new HttpServletResponseAdapter(response);
    ServerSentEventGenerator sse = new ServerSentEventGenerator(responseAdapter);

    Runnable listener =
        () -> {
          try {
            String boardHtml = gameService.getCachedBoardHtml();
            String fragment = buildBoardFragment(boardHtml);
            PatchElements event = PatchElements.builder().data(fragment).build();
            sse.send(event);
          } catch (Exception e) {
            closeLatch.countDown();
          }
        };

    gameService.addListener(listener);

    // Send initial board state
    try {
      String boardHtml = gameService.getCachedBoardHtml();
      String fragment = buildBoardFragment(boardHtml);
      PatchElements event = PatchElements.builder().data(fragment).build();
      sse.send(event);
    } catch (Exception e) {
      gameService.removeListener(listener);
      asyncContext.complete();
      return;
    }

    // Wait until connection drops
    Thread.ofVirtual()
        .name("sse-wait")
        .start(
            () -> {
              try {
                closeLatch.await();
              } catch (InterruptedException ignored) {
              } finally {
                gameService.removeListener(listener);
                try {
                  sse.close();
                } catch (Exception ignored) {
                }
                try {
                  asyncContext.complete();
                } catch (Exception ignored) {
                }
              }
            });
  }

  private static String buildBoardFragment(String boardHtml) {
    return "<div id=\"board\" class=\"board\" data-on:pointerdown=\"@post('/tap?id=' +"
        + " evt.target.dataset.id)\">"
        + boardHtml
        + "</div>";
  }

  @PostMapping("/tap")
  @ResponseBody
  public void tap(@RequestParam("id") int id) {
    int color = ThreadLocalRandom.current().nextInt(1, Game.COLORS.length);
    gameService.getGame().fillCross(id, color);
  }
}
