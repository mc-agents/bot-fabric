package kr.junhyung.mcagents.botfabric.task;

import kr.junhyung.mcagents.botfabric.rpc.CallContext;

public interface Task {
    String name();

    default void start(CallContext call) throws Exception {
    }

    boolean tick(CallContext call) throws Exception;

    default void cleanup(CallContext call) {
    }
}
