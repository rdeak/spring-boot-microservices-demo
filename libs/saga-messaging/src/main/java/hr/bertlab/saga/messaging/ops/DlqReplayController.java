package hr.bertlab.saga.messaging.ops;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("${saga.messaging.admin-path}")
public class DlqReplayController {

    private final DlqReplayer replayer;

    public DlqReplayController(DlqReplayer replayer) {
        this.replayer = replayer;
    }

    @PostMapping("/dlq/replay")
    public Map<String, Integer> replay(@RequestParam(name = "max", defaultValue = "100") int max) {
        if (max < 1 || max > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "max must be between 1 and 10000");
        }
        return Map.of("replayed", replayer.replay(max));
    }
}
