/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Proxied message handling lifted quite egregiously from mod-work-queue
 * https://github.com/vert-x/mod-work-queue
 */
package com.bloidonia.vertx.mods ;

import org.vertx.java.busmods.BusModBase ;
import org.vertx.java.core.Handler ;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.eventbus.Message;

public class CircuitBreakerProxy extends BusModBase implements Handler<Message<JsonObject>> {
  private String address ;
  private String proxiedAddress ;
  private CircuitBreakerState state ;
  private int failures ;
  private long lastFailure ;
  private long lastStateChange ;

  private int call_timeout ;
  private int max_failures ;
  private int reset_time ;


  public void start() {
    super.start() ;

    failures = 0 ;
    lastStateChange = System.currentTimeMillis() ;
    state = CircuitBreakerState.CLOSED ;

    max_failures = getMandatoryIntConfig( "max_failures" ) ;
    call_timeout = getMandatoryIntConfig( "call_timeout" ) * 1000 ;
    reset_time   = getMandatoryIntConfig( "reset_time" ) * 1000 ;

    address        = getMandatoryStringConfig( "address" ) ;
    proxiedAddress = getMandatoryStringConfig( "proxiedAddress" ) ;

    logger.info( String.format( "Registering %s as proxy for %s", address, proxiedAddress ) ) ;
    eb.registerHandler( address, CircuitBreakerProxy.this ) ;
  }

  public void stop() {
  }

  protected JsonObject getMandatoryObjectConfig(String fieldName) {
    JsonObject s = config.getObject( fieldName ) ;
    if( s == null ) {
      throw new IllegalArgumentException( fieldName + " must be specified in config for busmod");
    }
    return s ;
  }

  private void openBreaker() {
    lastStateChange = System.currentTimeMillis() ;
    state = CircuitBreakerState.OPEN ;
  }

  private void closeBreaker() {
    lastStateChange = System.currentTimeMillis() ;
    state = CircuitBreakerState.CLOSED ;
  }

  private void semiOpenBreaker() {
    lastStateChange = System.currentTimeMillis() ;
    state = CircuitBreakerState.SEMI_OPEN ;
  }

  private void incrementFailures( boolean semi ) {
    long now = System.currentTimeMillis() ;
    if( now - lastFailure > reset_time ) {
      failures = 0 ;
    }
    failures++ ;
    lastFailure = now ;
    if( semi || failures >= max_failures ) {
      openBreaker() ;
    }
  }

  public void handle( final Message<JsonObject> message ) {
    logger.info( String.format( "%s recieved %s for %s", address, proxiedAddress, message.body.encode() ) ) ;
    if( state == CircuitBreakerState.OPEN ||
        state == CircuitBreakerState.SEMI_OPEN ) {
      if( System.currentTimeMillis() - lastStateChange > reset_time ) {
        switch( state ) {
          case OPEN :
            semiOpenBreaker() ;
            break ;
          case SEMI_OPEN :
            closeBreaker() ;
            failures = 0 ;
            break ;
        }
      }
    }
    switch( state ) {
      case OPEN : {
        sendError( message,
                   String.format( "Circuit Breaker Open for %s", address ),
                   new CircuitBreakerException() ) ;
        break ;
      }
      case SEMI_OPEN :
      case CLOSED : {
        final long timeoutID = vertx.setTimer( call_timeout, new Handler<Long>() {
          public void handle( Long id ) {
            // Processor timed out - increment failures
            incrementFailures( state == CircuitBreakerState.SEMI_OPEN ) ;
            logger.warn( String.format( "Circuit Breaker Timeout. Current failure count %d", failures ) ) ;
            sendError( message,
                       String.format( "Circuit Breaker Timeout. Current failure count %d", failures ),
                       new CircuitBreakerException() ) ;
          }
        } );
        eb.send( proxiedAddress, message.body, new Handler<Message<JsonObject>>() {
          public void handle( Message<JsonObject> reply ) {
            messageReplied( message, reply, proxiedAddress, timeoutID ) ;
          }
        } ) ;
        break ;
      }
    }
  }

  private void messageReplied( final Message<JsonObject> message, final Message<JsonObject> reply,
                               final String proxyAddress,
                               final long timeoutID ) {
    if (reply.replyAddress != null) {
      // The reply itself has a reply specified so we don't consider the message processed just yet
      message.reply( reply.body, new Handler<Message<JsonObject>>() {
        public void handle( final Message<JsonObject> replyReply ) {
          reply.reply( replyReply.body, new Handler<Message<JsonObject>>() {
            public void handle( Message<JsonObject> replyReplyReply ) {
              messageReplied( new NonLoadedHolder( replyReply ), replyReplyReply, proxyAddress, timeoutID ) ;
            }
          } ) ;
        }
      } ) ;
    } else {
      messageProcessed( timeoutID, proxyAddress, message, reply ) ;
    }
  }

  private void messageProcessed( long timeoutID, 
                                 String proxyAddress, 
                                 Message<JsonObject> message,
                                 Message<JsonObject> reply ) {
    vertx.cancelTimer( timeoutID ) ;
    String status = reply.body.getString( "status" ) ;
    if( status != null && !status.equals( "ok" ) ) {
      incrementFailures( state == CircuitBreakerState.SEMI_OPEN ) ;
      logger.warn( String.format( "Circuit Breaker error status. Current failure count %d", failures ) ) ;
    }
    message.reply( reply.body, null ) ;
  }

  private static class NonLoadedHolder extends Message<JsonObject> {
    private final Message<JsonObject> message;

    private NonLoadedHolder( Message<JsonObject> message ) { this.message = message ; }
    public JsonObject getBody()                            { return message.body ;    }

    public void reply( JsonObject reply, Handler<Message<JsonObject>> replyReplyHandler ) {
      message.reply(reply, replyReplyHandler);
    }
  }
}