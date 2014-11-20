package org.widok

object Opt {
  def apply[T](value: T): Opt[T] = {
    val res = Opt[T]()
    res := value
    res
  }
}

/**
 * Publishes a stream of defined values. Use isEmpty() to detect when the
 * current value is cleared.
 */
case class Opt[T]() extends StateChannel[T] {
  private var cached: Option[T] = None
  private val defined = LazyVar[Boolean](cached.isDefined)

  attach { t =>
    val prevDefined = cached.isDefined
    cached = Some(t)
    if (!prevDefined) defined.produce()
  }

  def isEmpty: ReadChannel[Boolean] = defined.map(!_)
  def nonEmpty: ReadChannel[Boolean] = defined

  def isDefined: ReadChannel[Boolean] = defined

  def flush(f: T => Unit) {
    if (cached.isDefined) f(cached.get)
  }

  // TODO This does not work.
  //def size: ReadChannel[Int] =
  //  defined.flatMap { state =>
  //    if (!state) Var(0)
  //    else foldLeft(0) { case (acc, cur) => acc + 1 }
  //  }

  // Workaround
  def size: ReadChannel[Int] = {
    var count = 0
    val res = forkUniState(t => {
      count += 1
      Result.Next(Some(count))
    }, Some(count)).asInstanceOf[ChildChannel[Int, Int]]
    defined.attach(d ⇒ if (!d) {
      count = 0
      res := 0
    })
    res
  }

  def clear() {
    val prevDefined = cached.isDefined
    cached = None
    if (prevDefined) defined.produce()
  }

  /** @note This method may only be called if the value is defined. */
  def get: T = cached.get

  def getOrElse(default: => T): T = cached.getOrElse(default)
  def orElse(default: => ReadChannel[T]): ReadChannel[T] =
    defined.flatMap { value =>
      if (value) Var(cached.get)
      else default
    }

  def values: ReadChannel[Option[T]] =
    defined.map(_ => cached)

  def toOption: Option[T] = cached

  private def str = cached.map(_.toString).getOrElse("<undefined>")
  override def toString = s"Opt($str)"
}
