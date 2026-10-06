package io.github.praktimarc.mikrotik.facade.internal.stream;

import io.github.praktimarc.mikrotik.facade.RouterOsRecord;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikBackpressureException;
import io.github.praktimarc.mikrotik.facade.exception.MikrotikDataException;
import io.github.praktimarc.mikrotik.facade.internal.command.RouterOsCommand;
import me.legrange.mikrotik.ApiConnection;
import me.legrange.mikrotik.MikrotikApiException;
import me.legrange.mikrotik.ResultListener;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterOsPublisherTest {

    @Test void publisherIsCold() { FakeConnection c=new FakeConnection(); publisher(c,4,Runnable::run,Runnable::run).toString(); assertEquals(0,c.executions.get()); }
    @Test void eachSubscriptionStartsIndependentRouterOsOperation() { FakeConnection c=new FakeConnection(); var p=publisher(c,4,Runnable::run,Runnable::run); p.subscribe(new TestSubscriber()); p.subscribe(new TestSubscriber()); assertEquals(2,c.executions.get()); assertEquals(2,c.listeners.size()); }
    @Test void requestOneDeliversAtMostOneBufferedItem() throws Exception { FakeConnection c=new FakeConnection(); TestSubscriber s=new TestSubscriber(); publisher(c,8,Runnable::run,Runnable::run).subscribe(s); ResultListener l=c.listeners.get(0); l.receive(Map.of("n","1")); l.receive(Map.of("n","2")); l.receive(Map.of("n","3")); s.subscription.request(1); assertEquals(1,s.items.size()); assertEquals("1",s.items.get(0).require("n")); }
    @Test void demandArithmeticSaturates() { assertEquals(Long.MAX_VALUE,RouterOsSubscription.addCap(Long.MAX_VALUE-1,10)); assertEquals(9L,RouterOsSubscription.addCap(4,5)); }
    @Test void zeroAndNegativeRequestTerminateAccordingToFlowRules() { for(long n:new long[]{0,-1}){ FakeConnection c=new FakeConnection(); TestSubscriber s=new TestSubscriber(); publisher(c,4,Runnable::run,Runnable::run).subscribe(s); s.subscription.request(n); assertInstanceOf(IllegalArgumentException.class,s.error); } }
    @Test void boundedQueueOverflowSignalsBackpressureAndCancelsBestEffort() { FakeConnection c=new FakeConnection(); TestSubscriber s=new TestSubscriber(); publisher(c,2,Runnable::run,Runnable::run).subscribe(s); ResultListener l=c.listeners.get(0); l.receive(Map.of("n","1")); l.receive(Map.of("n","2")); l.receive(Map.of("n","3")); assertInstanceOf(MikrotikBackpressureException.class,s.error); assertEquals(1,c.cancels.get()); }
    @Test void overflowNeverSilentlyDropsAnEvent() { FakeConnection c=new FakeConnection(); TestSubscriber s=new TestSubscriber(); publisher(c,1,Runnable::run,Runnable::run).subscribe(s); ResultListener l=c.listeners.get(0); l.receive(Map.of("n","1")); l.receive(Map.of("n","2")); assertInstanceOf(MikrotikBackpressureException.class,s.error); }
    @Test void eventsRemainOrderedAndCompletionWaitsForBufferedDemand() throws Exception { FakeConnection c=new FakeConnection(); TestSubscriber s=new TestSubscriber(); publisher(c,8,Runnable::run,Runnable::run).subscribe(s); ResultListener l=c.listeners.get(0); l.receive(Map.of("n","1")); l.receive(Map.of("n","2")); l.receive(Map.of("n","3")); l.completed(); assertFalse(s.complete); s.subscription.request(3); assertEquals(List.of("1","2","3"),s.values()); assertTrue(s.complete); }
    @Test void callbacksForOneSubscriptionNeverExecuteConcurrently() throws Exception { FakeConnection c=new FakeConnection(); ExecutorService pool=Executors.newFixedThreadPool(4); try { CountDownLatch first=new CountDownLatch(1), release=new CountDownLatch(1), second=new CountDownLatch(1); AtomicInteger active=new AtomicInteger(), max=new AtomicInteger(); Flow.Subscriber<RouterOsRecord> s=new Flow.Subscriber<>() { public void onSubscribe(Flow.Subscription x){x.request(2);} public void onNext(RouterOsRecord r){int a=active.incrementAndGet();max.accumulateAndGet(a,Math::max); if(first.getCount()>0){first.countDown(); await(release);} else second.countDown(); active.decrementAndGet();} public void onError(Throwable t){} public void onComplete(){} }; publisher(c,8,Runnable::run,pool).subscribe(s); waitForListeners(c); ResultListener l=c.listeners.get(0); l.receive(Map.of("n","1")); assertTrue(first.await(2,TimeUnit.SECONDS)); l.receive(Map.of("n","2")); release.countDown(); assertTrue(second.await(2,TimeUnit.SECONDS)); assertEquals(1,max.get()); } finally { pool.shutdownNow(); } }
    @Test void cancellationYieldsNoLaterSignals() { FakeConnection c=new FakeConnection(); TestSubscriber s=new TestSubscriber(); publisher(c,8,Runnable::run,Runnable::run).subscribe(s); s.subscription.request(Long.MAX_VALUE); s.subscription.cancel(); ResultListener l=c.listeners.get(0); l.receive(Map.of("n","1")); l.completed(); l.error(new MikrotikApiException("late")); assertTrue(s.items.isEmpty()); assertFalse(s.complete); assertEquals(null, s.error); }
    @Test void streamHasNoGenericOverallCommandTimeout() { for(var f:RouterOsPublisher.class.getDeclaredFields()) assertFalse(f.getName().toLowerCase().contains("timeout")); for(var f:RouterOsSubscription.class.getDeclaredFields()) assertFalse(f.getName().toLowerCase().contains("timeout")); }
    @Test void slowSubscriberNeverBlocksRouterOsIoThread() throws Exception { FakeConnection c=new FakeConnection(); ExecutorService dispatch=Executors.newSingleThreadExecutor(); try { CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1), ioReturned=new CountDownLatch(1); Flow.Subscriber<RouterOsRecord> s=new Flow.Subscriber<>() { public void onSubscribe(Flow.Subscription x){x.request(2);} public void onNext(RouterOsRecord r){entered.countDown();await(release);} public void onError(Throwable t){} public void onComplete(){} }; publisher(c,8,dispatch,Runnable::run).subscribe(s); waitForListeners(c); ResultListener l=c.listeners.get(0); l.receive(Map.of("n","1")); assertTrue(entered.await(2,TimeUnit.SECONDS)); new Thread(()->{l.receive(Map.of("n","2"));ioReturned.countDown();},"routeros-processor").start(); assertTrue(ioReturned.await(2,TimeUnit.SECONDS)); release.countDown(); } finally { dispatch.shutdownNow(); } }
    @Test void cancellationBeforeReturnedTagIsPropagatedAfterTagAssignment() throws Exception { FakeConnection c=new FakeConnection(); c.blockTag=true; ExecutorService dispatch=Executors.newSingleThreadExecutor(); try { TestSubscriber s=new TestSubscriber(); publisher(c,8,dispatch,Runnable::run).subscribe(s); assertTrue(c.executeEntered.await(2,TimeUnit.SECONDS)); s.subscription.cancel(); assertEquals(0,c.cancels.get()); c.releaseTag.countDown(); assertTrue(c.cancelCalled.await(2,TimeUnit.SECONDS)); assertEquals(1,c.cancels.get()); } finally { dispatch.shutdownNow(); } }
    @Test void localMappingFailureAfterUpstreamDoneStillBecomesError() { FakeConnection c=new FakeConnection(); TestSubscriber s=new TestSubscriber(); RouterOsPublisher<RouterOsRecord> p=new RouterOsPublisher<>(c,"mapping stream",RouterOsCommand.builder("/test/listen").build(),4,Runnable::run,Runnable::run,r->{throw new MikrotikDataException("mapping failed");}); p.subscribe(s); ResultListener l=c.listeners.get(0); l.receive(Map.of("n","1")); l.completed(); s.subscription.request(1); assertInstanceOf(MikrotikDataException.class,s.error); assertFalse(s.complete); }

    private static RouterOsPublisher<RouterOsRecord> publisher(FakeConnection c,int cap,Executor dispatch,Executor callback){ return new RouterOsPublisher<>(c,"test stream",RouterOsCommand.builder("/test/listen").build(),cap,dispatch,callback,r->r); }
    private static final class TestSubscriber implements Flow.Subscriber<RouterOsRecord>{ Flow.Subscription subscription; final List<RouterOsRecord> items=new ArrayList<>(); Throwable error; boolean complete; public void onSubscribe(Flow.Subscription s){subscription=s;} public void onNext(RouterOsRecord r){items.add(r);} public void onError(Throwable t){error=t;} public void onComplete(){complete=true;} List<String> values() throws Exception { List<String> out=new ArrayList<>(); for(RouterOsRecord r:items) out.add(r.require("n")); return out; } }
    private static final class FakeConnection extends ApiConnection { final AtomicInteger executions=new AtomicInteger(), cancels=new AtomicInteger(); final List<ResultListener> listeners=Collections.synchronizedList(new ArrayList<>()); final CountDownLatch executeEntered=new CountDownLatch(1), releaseTag=new CountDownLatch(1), cancelCalled=new CountDownLatch(1); volatile boolean blockTag; public boolean isConnected(){return true;} public void login(String u,String p){} public List<Map<String,String>> execute(String c){throw new AssertionError("stream must use listener execute");} public String execute(String command,ResultListener listener) throws MikrotikApiException {int n=executions.incrementAndGet();listeners.add(listener);executeEntered.countDown();if(blockTag)try{releaseTag.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new MikrotikApiException("interrupted",e);}return "tag-"+n;} public long downloadFile(String r,Path l) throws IOException{throw new UnsupportedOperationException();} public void cancel(String tag){cancels.incrementAndGet();cancelCalled.countDown();} public void setTimeout(int t){} public void close(){} }
    private static void await(CountDownLatch l){try{l.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private static void waitForListeners(FakeConnection c) throws Exception { long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(2); while(c.listeners.isEmpty()&&System.nanoTime()<end) Thread.yield(); assertFalse(c.listeners.isEmpty()); }
}
