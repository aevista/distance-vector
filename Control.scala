package com.network.control

object Control {
  def apply[A](): Control[A,A] =
    new Control[A, A](a => (cb: A => Unit) => cb(a))

  def success[A, Any](a: => A): Control[A, Any] =
    new Control[A, Any](_ => (cb: A => Unit) => cb(a))
}

import Control._

class Control[A, Any] private(private val future: Any => (A => Unit) => Unit) {

  def map[B](f: A => B): Control[B, Any] =
    flatMap(a => success(f(a)))

  def flatMap[B](f: A => Control[B, Any]): Control[B, Any] =
    new Control[B,Any](any => cb => future(any){a => f(a).future(any)(cb)})

  def filter(f: A => Boolean): Control[A, Any] =
    new Control[A,Any](ack => cb => future(ack){a => success(a).future(ack){a => if (f(a)) cb(a)}})

  def andThen[B](f: A => B): Control[A, Any] =
    map(a => {f(a); a})

  def process(any: Any): Unit =
    future(any)(identity[A])

}

