/*
 * Copyright (c) 2013, Hidekatsu Hirose
 * Copyright (c) 2013, Hirose-Zouen
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

package org.hirosezouen.hzactor

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

import scala.util.control.Exception._
import scala.collection.generic.GenericSetTemplate
import scala.collection.generic.SetFactory
import scala.collection.generic.ImmutableSetFactory
import scala.collection.SetLike
import scala.concurrent.duration._

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.ActorRefFactory
import akka.actor.ActorContext
import akka.actor.Props
import akka.actor.Terminated

import org.hirosezouen.hzutil._
import HZIO._

object HZActor {
    import HZLog._
    implicit val logger = getLogger(this.getClass.getName)

    trait HZActorCommand
    case class HZStop() extends HZActorCommand 
    case class HZStopWithReason(reason: AnyRef) extends HZActorCommand 

    trait HZActorInformation

    trait HZActorReason
    trait HZActorStoped extends HZActorReason
    case class HZErrorStoped(th: Throwable) extends HZActorStoped
    case class HZNormalStoped() extends HZActorStoped
    case class HZNormalStopedWithMessage(message: String) extends HZActorStoped
    case class HZCommandStoped() extends HZActorStoped
    case class HZCommandStopedWithReason(reason: AnyRef) extends HZActorStoped
    case class HZUnHandledException(reason: Any) extends HZActorReason
    case class HZUnknownReason(reason: Any) extends HZActorReason
    object HZNullReason extends HZActorReason

    trait HZActorStateBase {
        val actor: ActorRef
        val stopReason: HZActorReason
    }

    case class HZActorState(actor: ActorRef, stopReason: HZActorReason = HZNullReason) extends HZActorStateBase
    case class HZActorStates(context: ActorContext, var actorStateSet: Set[HZActorState] = Set.empty) {
    //    import HZActorStates._

        def add(as: HZActorState): Unit = {
            actorStateSet += as
            context.watch(as.actor)
        }
        def delete(a: ActorRef): Unit = actorStateSet = actorStateSet.filterNot{
            as =>
            if(as.actor == a) {
                context.unwatch(a)
                false
            } else
                true
        }
        def add(a: ActorRef): Unit = add(HZActorState(a))
        def addReason(a: ActorRef, reason: HZActorReason): Unit = 
            actorStateSet = actorStateSet.map(as => if(as.actor == a) HZActorState(a, reason) else as)
        def +=(as: HZActorState): Unit = add(as)
        def +=(a: ActorRef): Unit = add(a)
    //    def ++=(assw: HZActorStateSeqWrap): Unit = actorStateSet ++= assw.seq
    //    def ++=(actorsw: ActorRefSeqWrap): Unit = actorStateSet ++= actorsw.seq.map(HZActorState(_))
        def ++=(actors: Seq[ActorRef]): Unit = actors.foreach(add(_))
        def +=(actors: ActorRef*): Unit = this.++=(actors)
        def -=(a: ActorRef): Unit = delete(a)
        def contains(a: ActorRef): Boolean = actorStateSet.find(_.actor == a).isEmpty
        def isEmpty: Boolean = actorStateSet.isEmpty
        def nonEmpty: Boolean = actorStateSet.nonEmpty
        def size: Int = actorStateSet.size

        def foreach(f: (HZActorState) => Unit): Unit = actorStateSet.foreach(f(_))
    }
    object HZActorStates {
    //    case class HZActorStateSeqWrap(seq: Seq[HZActorState])
    //    case class ActorRefSeqWrap(seq: Seq[ActorRef])
    //    implicit def seq2HZActorStateSeqWrap(seq: Seq[HZActorState]) = HZActorStateSeqWrap(seq)
    //    implicit def seq2ActorRefSeqWrap(seq: Seq[ActorRef]) = ActorRefSeqWrap(seq)

    //    def apply(assw: HZActorStateSeqWrap): HZActorStates = HZActorStates(Set(assw.seq: _*))
    //    def apply(actorsw: ActorRefSeqWrap): HZActorStates = HZActorStates(Set(actorsw.seq.map(HZActorState(_)): _*))
        def apply(actors: ActorRef*)(implicit context: ActorContext): HZActorStates
            = HZActorStates(context, Set(actors.map(HZActorState(_)): _*))
    }

    /*
     * ! prohibit use !
     * Note : I gave up implement companion object because I could not be solved "Higher Kind Generics" problem anyhow.
     * reference : http://stackoverflow.com/questions/4416885/extend-scala-set-with-concrete-type
     *           : http://stackoverflow.com/questions/27800502/error-higher-kinded-types-scala-type-arguments-do-not-conform-type-ts-bounds
     */
    import scala.language.higherKinds
    import scala.collection.AbstractSet
    import scala.collection.generic.{CanBuildFrom,GenericCompanion,GenericSetTemplate,SetFactory}
    import scala.collection.mutable.Builder
    import scala.collection.mutable.SetBuilder
    import scala.collection.SetLike
    class HZActorStateSet[A <: HZActorState](set: Set[A] = Set.empty[A])
        extends AbstractSet[A]
        with Set[A]
        with SetLike[A, HZActorStateSet[A]]
//    with GenericSetTemplate[A, HZActorStateSet]
        with Serializable
    {
        def +(elem: A): HZActorStateSet[A] = new HZActorStateSet(set + elem)
        def -(elem: A): HZActorStateSet[A] = new HZActorStateSet(set - elem)
        def contains(elem: A): Boolean = set.contains(elem)
        def iterator: Iterator[A] = set.iterator
        override def empty = new HZActorStateSet()
//    override def companion: GenericCompanion[HZActorStateSet] = HZActorStateSet
    }
//    object HZActorStateSet extends ImmutableSetFactory[HZActorStateSet]
    object HZActorStateSet
    {
        def empty[A <: HZActorState]: HZActorStateSet[A] = new HZActorStateSet[A]()
        private def newBuilder[A <: HZActorState]: Builder[A, HZActorStateSet[A]] = new SetBuilder[A, HZActorStateSet[A]](empty)
        def apply[A <: HZActorState](elems: A*): HZActorStateSet[A] = (empty[A] /: elems) (_ + _)
        private def testSetCanBuildFrom[A <: HZActorState] = new CanBuildFrom[HZActorStateSet[A], A, HZActorStateSet[A]] {
            def apply(from: HZActorStateSet[A]) = newBuilder
            def apply() = newBuilder
        }
    }

    def exitNormaly(reason: HZActorReason, parent: ActorRef)(implicit myself: ActorRef, context: ActorContext) {
        parent ! reason
        context.stop(myself)
    }
    def exitNormaly(parent: ActorRef)(implicit myself: ActorRef, context: ActorContext) {
        exitNormaly(HZNormalStoped(), parent)(myself, context)
    }

    def exitWithError(reason: HZActorReason, th: Throwable, parent: ActorRef):Nothing = {
        parent ! reason
        throw th
    }
    def exitWithError(th: Throwable, parent: ActorRef):Nothing = {
        exitWithError(HZErrorStoped(th), th, parent)
    }

    type PFInput = PartialFunction[String, Unit]
    def defaultInputFilter(s: String) = s 
    class InputActor(in: InputStream, filter: (String) => String,
                     input: PFInput) extends Actor
    {
        private val reader = new BufferedReader(new InputStreamReader(in))
        private case class InputLoop()

        override def preStart() {
            log_trace(s"InputActor:$self")
            self ! InputLoop()
        }

        def receive = {
            case InputLoop() => {
                catching(classOf[Exception]) either {
                    reader.readLine
                } match {
                    case Right(line) => {
                        log_trace(s"InputActor:Rignt($line)")
                        (({
                            case null => {
                                log_trace("InputActor:Right(null)")
//                                parent ! HZNormalStoped()
                                context.stop(self)
                            }
                        }: PFInput) orElse input orElse({
                            case x => log_error(s"InputActor:unknown message:$x")
                        }: PFInput))(filter(line))

                        self ! InputLoop()
                    }
                    case Left(th) => {
                        log_error(s"InputActor:Left($th.toString)")
//                        parent ! HZErrorStoped(th)
                        context.stop(self)
                    }
                }
            }
        }
    }
    object InputActor {
        def start(in: InputStream, filter: (String) => String = defaultInputFilter)
                 (input: PFInput)
                 (implicit context: ActorContext): ActorRef
            = context.actorOf(Props(new InputActor(in,filter,input)), "InputActor")
    }
}

/* vim: set expandtab: */
